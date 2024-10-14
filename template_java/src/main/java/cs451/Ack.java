package cs451;

import java.io.Serializable;

public class Ack implements Serializable {
    private String messageId;

    public Ack(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }
}
