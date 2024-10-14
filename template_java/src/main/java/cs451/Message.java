package cs451;

import java.io.Serializable;

public class Message implements Serializable {
    private int senderId;
    private int seqNum;
    private byte[] payload;

    public Message(int senderId, int seqNum, byte[] payload) {
        this.senderId = senderId;
        this.seqNum = seqNum;
        this.payload = payload;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public byte[] getPayload() {
        return payload;
    }
}
