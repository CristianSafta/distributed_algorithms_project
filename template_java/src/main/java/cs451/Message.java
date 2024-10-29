package cs451;

import java.io.Serializable;

public class Message implements Serializable {
    private int senderId;
    private int seqNum;
    private byte[] payload;
    private final int destinationId;

    public Message(int senderId, int destinationId, int seqNum, byte[] payload) {
        this.senderId = senderId;
        this.seqNum = seqNum;
        this.payload = payload;
        this.destinationId = destinationId;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getDestinationId() {
        return destinationId;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public byte[] getPayload() {
        return payload;
    }
}
