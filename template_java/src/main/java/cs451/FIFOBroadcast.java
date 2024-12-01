package cs451;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FIFOBroadcast {
    private final UniformReliableBroadcast urb;
    private final int processId;
    private final Map<Integer, Integer> next = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Integer, Message>> holdBackQueue = new ConcurrentHashMap<>();

    public FIFOBroadcast(UniformReliableBroadcast urb, int processId) {
        this.urb = urb;
        this.processId = processId;

        // Set up the deliver callback
        this.urb.setDeliverCallback(this::onUrbDeliver);
    }

    public void frbBroadcast(Message message) {
        // URB broadcast the message
        urb.urbBroadcast(message);
    }

    private void onUrbDeliver(Message message) {
        int senderId = message.getSenderId();
        int seqNum = message.getSeqNum();

        // Initialize next[senderId] if not present
        next.putIfAbsent(senderId, 1);

        int expectedSeqNum = next.get(senderId);

        if (seqNum == expectedSeqNum) {
            // Deliver the message
            deliver(message);

            // Increment next expected sequence number
            next.put(senderId, expectedSeqNum + 1);

            // Check the hold-back queue for any buffered messages
            Map<Integer, Message> senderQueue = holdBackQueue.get(senderId);
            if (senderQueue != null) {
                while (senderQueue.containsKey(next.get(senderId))) {
                    Message nextMessage = senderQueue.remove(next.get(senderId));
                    deliver(nextMessage);
                    next.put(senderId, next.get(senderId) + 1);
                }
            }
        } else if (seqNum > expectedSeqNum) {
            // Buffer the message
            holdBackQueue.putIfAbsent(senderId, new ConcurrentHashMap<>());
            holdBackQueue.get(senderId).put(seqNum, message);
        }
        // Else, seqNum < expectedSeqNum, message already delivered, do nothing
    }

    private void deliver(Message message) {
        // Deliver the message to the application layer
        // Log the delivery event
        String logEntry = "d " + message.getSenderId() + " " + message.getSeqNum();
        Main.logEvent(logEntry);

        if ((message.getSeqNum() % 1000) == 0) {
            System.out.println("FIFO Delivered message from Process " + message.getSenderId() + ": SeqNum " + message.getSeqNum());
        }
    }
}
