package cs451;

import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

public class PerfectLinks implements AutoCloseable, SharedInterfaces.ChannelSnapshot, Runnable {
    private final DatagramSocket socket;
    private final short selfId;
    private final Map<Short, NodeAddress> nodesMap;
    private final ParticipantRole myRole;
    private final SharedInterfaces.MsgReception parent;
    private final ConcurrentMsgHistory deliveredSet;
    private final ConcurrentMsgHistory ackedSet;
    private final ConcurrentLinkedQueue<NetMessage.NetCarrier> pendingSends;
    private final ConcurrentHashMap.KeySetView<NetMessage.NetCarrier, Boolean> retrySet;
    private long baseTimeout = GlobalParams.PL_TIMEOUT_BEFORE_RESEND;
    private long lastAdjust = System.currentTimeMillis();
    private final int ds;

    // Constructor for initiator
    public PerfectLinks(short myId, Map<Short, NodeAddress> hMap, ConfigurationFileReader.NestedConfig config)
            throws SocketException, UnknownHostException {
        if (hMap == null || config == null) {
            throw new IllegalArgumentException("Null arguments for ReliableChannel (initiator)");
        }
        this.selfId = myId;
        this.nodesMap = hMap;
        NodeAddress selfHost = this.nodesMap.get(myId);
        InetAddress selfIP = InetAddress.getByName(selfHost.getIp());
        this.socket = new DatagramSocket(null);
        this.socket.setReuseAddress(true);
        this.socket.bind(new InetSocketAddress(selfIP, selfHost.getPortNumber()));

        this.socket.setSoTimeout(GlobalParams.SOCKET_TIMEOUT);
        this.ackedSet = new ConcurrentMsgHistory(config.getP(), config.getVs());
        this.myRole = ParticipantRole.INITIATOR;
        this.pendingSends = new ConcurrentLinkedQueue<>();
        this.retrySet = ConcurrentHashMap.newKeySet(GlobalParams.MAX_PL_QUEUE_SIZE);
        this.ds = config.getDs();
        this.parent = null;
        this.deliveredSet = null;
    }

    // Constructor for follower
    public PerfectLinks(short myId, Map<Short, NodeAddress> nodes, SharedInterfaces.MsgReception prt,
                        ConfigurationFileReader.NestedConfig cfg, ChannelState chSt) {
        if (prt == null || nodes == null || cfg == null || chSt == null) {
            throw new IllegalArgumentException("Null args for ReliableChannel (follower)");
        }
        this.selfId = myId;
        this.nodesMap = nodes;
        this.socket = chSt.getSocket();
        this.ackedSet = chSt.getAckSet();
        this.pendingSends = chSt.getOutbound();
        this.deliveredSet = new ConcurrentMsgHistory(cfg.getP(), cfg.getVs());
        this.parent = prt;
        this.myRole = ParticipantRole.FOLLOWER;
        this.ds = cfg.getDs();
        this.retrySet = null;
    }

    public boolean scheduleToSend(NetMessage msg, short dest) {
        if (msg == null) {
            throw new IllegalArgumentException("Cannot send null message");
        }
        return pendingSends.size() > GlobalParams.MAX_PL_QUEUE_SIZE ? false : pendingSends.add(msg.toSend(dest, true));
    }

    public void flush(int agreementId) {
        ackedSet.flush(agreementId);
        if (deliveredSet != null) {
            deliveredSet.flush(agreementId);
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        try {
            if (myRole == ParticipantRole.INITIATOR) {
                while (!Thread.currentThread().isInterrupted()) {
                    runInitiatorSide();
                }
            } else {
                while (!Thread.currentThread().isInterrupted()) {
                    runFollowerSide();
                }
            }
        } catch (InterruptedException e) {
            System.err.println("ReliableChannel interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private void runInitiatorSide() throws InterruptedException {
        NetMessage.NetCarrier carrier = pendingSends.poll();
        if (carrier != null) {
            sendDatagram(carrier);
            if (!carrier.getMessage().isPureAck()) {
                carrier.setTimeOfSending(System.currentTimeMillis());
                carrier.setTimeout(baseTimeout);
                retrySet.add(carrier);
            }
        } else {
            Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
        }

        long now = System.currentTimeMillis();
        final AtomicInteger retried = new AtomicInteger(0);
        int retryCount = retrySet.size();
        retrySet.removeIf(x -> {
            if (ackedSet.known(x)) {
                return true;
            } else if ((now - x.getTimeOfSending()) > x.getTimeout()) {
                pendingSends.add(x);
                retried.incrementAndGet();
                return true;
            }
            return false;
        });
        if (retried.get() > retryCount / 4 && (now - lastAdjust) > baseTimeout) {
            baseTimeout <<= 1;
            lastAdjust = now;
            System.out.println("Adjusted Timeout to " + baseTimeout);
        }
    }

    private void runFollowerSide() {
        receiveAndHandle();
    }

    private void receiveAndHandle() {
        if (myRole != ParticipantRole.FOLLOWER) {
            throw new IllegalStateException("Initiator cannot deliver messages");
        }
        NetMessage msg = fetchMessage();
        if (msg != null && !deliveredSet.known(msg.toSend(selfId, false)) && !msg.isPureAck()) {
            parent.receiveDeliveredMessage(msg);
            deliveredSet.record(msg.toSend(selfId, false));
        }
    }

    private void sendDatagram(NetMessage.NetCarrier c) {
        if (c == null) {
            throw new IllegalArgumentException("Cannot send null carrier");
        }
        NodeAddress dest = nodesMap.get(c.getDest());
        byte[] data = c.getSerializedMsg();
        DatagramPacket pkt = new DatagramPacket(data, data.length, dest.getSocketAddress());
        try {
            socket.send(pkt);
        } catch (IOException e) {
            System.err.println("Error sending datagram");
            e.printStackTrace();
        }
    }

    private NetMessage fetchMessage() {
        byte[] buffer = new byte[16 + Integer.BYTES * (ds + 1)];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (IOException e) {
            if (!(e instanceof SocketTimeoutException)) {
                System.err.println("Error receiving datagram");
                e.printStackTrace();
            }
            return null;
        }
        NetMessage mm = NetMessage.deserialize(packet.getData());
        if (mm != null) {
            if (mm.isPureAck()) {
                ackedSet.record(mm.toSend(mm.getSenderId(), false));
            } else {
                pendingSends.add(mm.ackReply(selfId).toSend(mm.getSenderId(), true));
                return mm;
            }
        }
        return null;
    }

    @Override
    public ChannelState snapshotChannel() {
        return new ChannelState(socket, ackedSet, pendingSends);
    }
}
