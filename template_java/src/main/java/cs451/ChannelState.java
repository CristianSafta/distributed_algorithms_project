package cs451;

import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChannelState {
    private final DatagramSocket sock;
    private final ConcurrentMsgHistory ackedMsgs;
    private final ConcurrentLinkedQueue<NetMessage.NetCarrier> outboundQueue;

    public ChannelState(DatagramSocket socket,
                        ConcurrentMsgHistory acked,
                        ConcurrentLinkedQueue<NetMessage.NetCarrier> toSendQueue) {
        if (socket == null || acked == null || toSendQueue == null) {
            throw new IllegalArgumentException("Null args in ChannelState");
        }
        this.sock = socket;
        this.ackedMsgs = acked;
        this.outboundQueue = toSendQueue;
    }

    public DatagramSocket getSocket() {
        return sock;
    }

    public ConcurrentMsgHistory getAckSet() {
        return ackedMsgs;
    }

    public ConcurrentLinkedQueue<NetMessage.NetCarrier> getOutbound() {
        return outboundQueue;
    }
}
