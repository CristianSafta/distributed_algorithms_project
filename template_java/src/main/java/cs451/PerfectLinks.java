package cs451;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PerfectLinks {
    private final int processId;
    private final int port;
    private final Map<Integer, Host> hosts;
    private final DatagramSocket socket;
    private final Set<String> deliveredMessages = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> ackedMessages = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Message> pendingMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retransmissionScheduler = Executors.newScheduledThreadPool(8);
    private MessageHandler messageHandler;

    public PerfectLinks(int processId, int port, Map<Integer, Host> hosts) throws SocketException {
        this.processId = processId;
        this.port = port;
        this.hosts = hosts;

        DatagramSocket newSocket = new DatagramSocket(null);
        newSocket.setReuseAddress(true);
        newSocket.bind(new InetSocketAddress(port));
        this.socket = newSocket;

        // Start retransmission
        retransmissionScheduler.scheduleAtFixedRate(this::retransmitPendingMessages, 0, 100, TimeUnit.MILLISECONDS);
    }

    public interface MessageHandler {
        void onMessageReceived(Message message);
    }

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public void send(int destinationId, Message message) {
        String messageId = message.getSenderId() + "-" + message.getSeqNum() + "-" + destinationId;
        pendingMessages.put(messageId, message);

        // Send the message immediately
        try {
            Host destinationHost = hosts.get(destinationId);
            sendMessage(destinationHost, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void retransmitPendingMessages() {
        for (Map.Entry<String, Message> entry : pendingMessages.entrySet()) {
            String key = entry.getKey();
            Message message = entry.getValue();

            // Extract destinationId from the key
            String[] parts = key.split("-");
            int destinationId = Integer.parseInt(parts[2]); // Key format: senderId-seqNum-destinationId

            try {
                Host destinationHost = hosts.get(destinationId);
                sendMessage(destinationHost, message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(Host host, Message message) throws IOException {
        InetAddress address = InetAddress.getByName(host.getIp());
        int destPort = host.getPort();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream outStream = new ObjectOutputStream(byteStream);
        outStream.writeObject(message);
        byte[] buffer = byteStream.toByteArray();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, destPort);
        socket.send(packet);
    }

    public void startReceiver() {
        new Thread(() -> {
            try {
                receive();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void receive() throws IOException, ClassNotFoundException {
        byte[] buffer = new byte[2048]; // Adjust buffer size if necessary

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
            ObjectInputStream inStream = new ObjectInputStream(byteStream);
            Object receivedObject = inStream.readObject();

            if (receivedObject instanceof Message) {
                Message message = (Message) receivedObject;
                String messageId = message.getSenderId() + "-" + message.getSeqNum();

                // Send ACK back
                sendAck(packet.getAddress(), packet.getPort(), messageId);

                // Deliver the message if not already delivered
                if (!deliveredMessages.contains(messageId)) {
                    deliveredMessages.add(messageId);
                    if (messageHandler != null) {
                        messageHandler.onMessageReceived(message);
                    }
                }
            } else if (receivedObject instanceof Ack) {
                Ack ack = (Ack) receivedObject;
                String ackMessageId = ack.getMessageId() + "-" + ack.getAckSenderId();
                ackedMessages.add(ackMessageId);
                pendingMessages.remove(ackMessageId);
            }
        }
    }

    private void sendAck(InetAddress address, int port, String messageId) throws IOException {
        Ack ack = new Ack(messageId, processId);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream outStream = new ObjectOutputStream(byteStream);
        outStream.writeObject(ack);
        byte[] buffer = byteStream.toByteArray();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }

    public void close() {
        socket.close();
        retransmissionScheduler.shutdown();
    }
}
