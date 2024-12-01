package cs451;

import java.io.Serializable;

public class Message implements Serializable {
    private int senderId;
    private int seqNum;
    private byte[] payload;
    private String type; // New field

    public Message(int senderId, int seqNum, byte[] payload) {
        this.senderId = senderId;
        this.seqNum = seqNum;
        this.payload = payload;
        this.type = "DATA"; // Default type
    }

    // Getters and setters
    public int getSenderId() {
        return senderId;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
