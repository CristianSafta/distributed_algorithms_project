package cs451;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UniformReliableBroadcast {
    private final PerfectLinks perfectLinks;
    private final Set<String> delivered = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final int processId;
    private final Map<Integer, Host> hosts;
    private DeliverCallback deliverCallback;

    public interface DeliverCallback {
        void deliver(Message message);
    }

    public UniformReliableBroadcast(PerfectLinks perfectLinks, int processId, Map<Integer, Host> hosts) {
        this.perfectLinks = perfectLinks;
        this.processId = processId;
        this.hosts = hosts;

        // Set up PerfectLinks message handler
        this.perfectLinks.setMessageHandler(this::onPlDeliver);
    }

    public void setDeliverCallback(DeliverCallback callback) {
        this.deliverCallback = callback;
    }

    public void urbBroadcast(Message message) {
        // Send to all processes using Perfect Links
        for (Integer destId : hosts.keySet()) {
            if (destId != processId) {
                Message msgCopy = new Message(message.getSenderId(), message.getSeqNum(), message.getPayload());
                msgCopy.setType("URB");
                perfectLinks.send(destId, msgCopy);
            }
        }
    }

    private void onPlDeliver(Message message) {
        String messageId = message.getSenderId() + "-" + message.getSeqNum();

        if (!delivered.contains(messageId)) {
            delivered.add(messageId);

            // Rebroadcast the message to all processes
            for (Integer destId : hosts.keySet()) {
                if (destId != processId) {
                    Message msgCopy = new Message(message.getSenderId(), message.getSeqNum(), message.getPayload());
                    msgCopy.setType("URB");
                    perfectLinks.send(destId, msgCopy);
                }
            }

            // Deliver the message to the upper layer
            if (deliverCallback != null) {
                deliverCallback.deliver(message);
            }
        }
    }
}
