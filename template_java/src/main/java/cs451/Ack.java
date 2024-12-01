package cs451;

import java.io.Serializable;

public class Ack implements Serializable {
    private String messageId;
    private int ackSenderId;

    public Ack(String messageId, int ackSenderId) {
        this.messageId = messageId;
        this.ackSenderId = ackSenderId;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getAckSenderId() {
        return ackSenderId;
    }
}
