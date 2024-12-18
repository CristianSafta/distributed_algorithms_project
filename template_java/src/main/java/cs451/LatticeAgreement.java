package cs451;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.util.concurrent.locks.ReentrantLock;

public class LatticeAgreement {

    private final int processId;
    private final Map<Integer, Host> hosts;
    private final PerfectLinks pl;
    private final int f; // max failures = (N-1)/2 for simplicity
    private int numSlots;
    private Map<Integer, SlotState> slotStates = new ConcurrentHashMap<>();
    private ReentrantLock decisionLock = new ReentrantLock();
    private BufferedWriter outputWriter;

    // Represent the state of each slot
    private static class SlotState {
        // Current proposal number for this slot
        AtomicInteger proposalNumber = new AtomicInteger(0);

        // The set initially proposed by this process
        Set<Integer> initialProposal = new HashSet<>();

        // The current "merged" proposal value that is being attempted
        Set<Integer> currentValue = new HashSet<>();

        // For counting ack/nack responses in the current round
        Map<Integer, Boolean> responses = new ConcurrentHashMap<>();
        // If decided
        boolean decided = false;

        // Once decided, store the decided value
        Set<Integer> decidedValue = new HashSet<>();

        SlotState(Set<Integer> proposal) {
            initialProposal.addAll(proposal);
            currentValue.addAll(proposal);
        }
    }

    public LatticeAgreement(int processId, Map<Integer, Host> hosts, PerfectLinks pl, int numSlots, String outputPath) {
        this.processId = processId;
        this.hosts = hosts;
        this.pl = pl;
        this.numSlots = numSlots;

        int N = hosts.size();
        this.f = (N - 1) / 2; // simple assumption

        try {
            outputWriter = new BufferedWriter(new FileWriter(outputPath));
        } catch(IOException e) {
            e.printStackTrace();
        }

        // Register message handler for Lattice Agreement messages
        this.pl.setMessageHandler((Message m) -> {
            // Only handle LatticeAgreement messages
            handleIncomingMessage(m);
        });
    }

    public void propose(int slot, Set<Integer> proposal) {
        // Initialize slot state
        SlotState state = new SlotState(proposal);
        slotStates.put(slot, state);

        // Start first proposal attempt
        sendProposal(slot, state.proposalNumber.get(), state.currentValue);
    }

    private void sendProposal(int slot, int proposalNumber, Set<Integer> value) {
        SlotState state = slotStates.get(slot);
        if (state == null || state.decided) {
            return;
        }

        // Clear previous responses
        state.responses.clear();

        LatticeMessage msg = new LatticeMessage(LatticeMessage.Type.PROPOSE, slot, proposalNumber, value, processId);
        broadcastLatticeMessage(msg);
    }

    // Broadcast message to all including self? Usually to all others
    private void broadcastLatticeMessage(LatticeMessage lMsg) {
        byte[] payload = serializeLatticeMessage(lMsg);
        // send to all
        for (Integer destId : hosts.keySet()) {
            if (destId != processId) {
                Message msg = new Message(processId, createSeqNum(), payload);
                msg.setType("LA");
                pl.send(destId, msg);
            }
        }
    }

    private byte[] serializeLatticeMessage(LatticeMessage lMsg) {
        try {
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            ObjectOutputStream oOut = new ObjectOutputStream(bOut);
            oOut.writeObject(lMsg);
            oOut.flush();
            return bOut.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private LatticeMessage deserializeLatticeMessage(byte[] data) {
        try {
            ByteArrayInputStream bIn = new ByteArrayInputStream(data);
            ObjectInputStream oIn = new ObjectInputStream(bIn);
            return (LatticeMessage)oIn.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Handle incoming messages of type LA
    private void handleIncomingMessage(Message m) {
        if (!"LA".equals(m.getType())) {
            // Not our message type, ignore (could be URB/FIFO messages)
            return;
        }

        LatticeMessage lm = deserializeLatticeMessage(m.getPayload());
        if (lm == null) {
            return;
        }

        switch (lm.getType()) {
            case PROPOSE:
                handlePropose(lm);
                break;
            case ACK:
                handleAck(lm);
                break;
            case NACK:
                handleNack(lm);
                break;
            case DECIDE:
                handleDecide(lm);
                break;
        }
    }

    // Acceptor logic: On PROPOSE
    private void handlePropose(LatticeMessage msg) {
        int slot = msg.getSlot();
        int propNum = msg.getProposalNumber();
        SlotState state = slotStates.get(slot);

        if (state == null) {
            // If we never proposed anything ourselves for this slot, initialize with empty proposal
            state = new SlotState(new HashSet<>());
            slotStates.put(slot, state);
        }

        if (state.decided) {
            // Already decided, just respond with DECIDE
            LatticeMessage decideMsg = new LatticeMessage(
                    LatticeMessage.Type.DECIDE,
                    slot,
                    state.proposalNumber.get(),
                    state.decidedValue,
                    processId
            );
            sendLatticeMessage(decideMsg, msg.getSenderId());
            return;
        }

        Set<Integer> proposedValues = msg.getValues();
        Set<Integer> currentAccepted = state.currentValue;

        int currentPropNum = state.proposalNumber.get();

        // If incoming proposalNumber >= current known proposalNumber:
        if (propNum >= currentPropNum) {
            // Check for consistency:
            // If proposedValues includes all of currentAccepted, we can ACK.
            // Otherwise, we NACK with a merged set.
            if (currentAccepted.isEmpty() || proposedValues.containsAll(currentAccepted)) {
                // Accept (ACK)
                // Update our known proposal number if needed
                if (propNum > currentPropNum) {
                    state.proposalNumber.set(propNum);
                }

                // **Important change**:
                // Previously, we replaced currentValue with proposedValues+initialProposal.
                // Now we ensure we keep all previously accepted values as well.
                Set<Integer> newValue = new HashSet<>(currentAccepted);
                newValue.addAll(proposedValues);
                newValue.addAll(state.initialProposal);
                state.currentValue = newValue;

                LatticeMessage ackMsg = new LatticeMessage(
                        LatticeMessage.Type.ACK,
                        slot,
                        propNum,
                        state.currentValue,
                        processId
                );
                sendLatticeMessage(ackMsg, msg.getSenderId());
            } else {
                // Conflict -> NACK with merged values
                // Merge proposedValues, currentAccepted, and initialProposal to ensure monotonic growth
                Set<Integer> merged = new HashSet<>(currentAccepted);
                merged.addAll(proposedValues);
                merged.addAll(state.initialProposal);

                // Update our proposal number
                state.proposalNumber.set(propNum);
                state.currentValue = merged; // keep merged as the current accepted set

                LatticeMessage nackMsg = new LatticeMessage(
                        LatticeMessage.Type.NACK,
                        slot,
                        propNum,
                        merged,
                        processId
                );
                sendLatticeMessage(nackMsg, msg.getSenderId());
            }
        } else {
            // The proposer is behind our known proposal number, send NACK with our current set
            Set<Integer> merged = new HashSet<>(state.currentValue);
            // merged already includes initialProposal and any previously accepted values
            LatticeMessage nackMsg = new LatticeMessage(
                    LatticeMessage.Type.NACK,
                    slot,
                    currentPropNum,
                    merged,
                    processId
            );
            sendLatticeMessage(nackMsg, msg.getSenderId());
        }
    }



    private void handleAck(LatticeMessage msg) {
        int slot = msg.getSlot();
        SlotState state = slotStates.get(slot);
        if (state == null || state.decided) {
            return;
        }

        int currentPropNum = state.proposalNumber.get();
        if (msg.getProposalNumber() == currentPropNum) {
            // Merge ACK values into our currentValue to ensure we never lose any known values
            Set<Integer> merged = new HashSet<>(state.currentValue);
            merged.addAll(msg.getValues());
            merged.addAll(state.initialProposal);
            state.currentValue = merged;

            state.responses.put(msg.getSenderId(), true);
            checkForDecision(slot);
        }
        // If proposal number doesn't match, we ignore the ACK as it's obsolete.
    }


    private void handleNack(LatticeMessage msg) {
        int slot = msg.getSlot();
        SlotState state = slotStates.get(slot);
        if (state == null || state.decided) {
            return;
        }

        int currentPropNum = state.proposalNumber.get();
        if (msg.getProposalNumber() == currentPropNum) {
            // Merge sets
            Set<Integer> merged = new HashSet<>(state.currentValue);
            merged.addAll(msg.getValues());
            // Ensure we never lose the initial proposal
            merged.addAll(state.initialProposal);

            state.currentValue = merged;
            int newPropNum = currentPropNum + 1;
            state.proposalNumber.set(newPropNum);

            sendProposal(slot, newPropNum, state.currentValue);
        }
    }

    private void handleDecide(LatticeMessage msg) {
        int slot = msg.getSlot();
        SlotState state = slotStates.get(slot);
        if (state == null) {
            // If we never saw this slot before, initialize
            state = new SlotState(new HashSet<>());
            slotStates.put(slot, state);
        }

        // Merge DECIDE values into currentValue before finalizing
        Set<Integer> merged = new HashSet<>(state.currentValue);
        merged.addAll(msg.getValues());
        merged.addAll(state.initialProposal);
        state.currentValue = merged;

        if (!state.decided) {
            state.decided = true;
            state.decidedValue = new HashSet<>(state.currentValue);
            writeDecision(slot, state.decidedValue);
        }
    }


    // Once we have f+1 acks, we can decide
    private void checkForDecision(int slot) {
        SlotState state = slotStates.get(slot);
        if (state == null || state.decided) {
            return;
        }

        int ackCount = 0;
        for (Map.Entry<Integer, Boolean> e : state.responses.entrySet()) {
            if (e.getValue()) {
                ackCount++;
            }
        }

        // if ackCount >= f+1, we can decide
        if (ackCount >= f + 1) {
            state.decided = true;
            // currentValue should already include initialProposal,
            // but let's be extra sure if you want:
            state.currentValue.addAll(state.initialProposal);

            state.decidedValue = new HashSet<>(state.currentValue);

            LatticeMessage decideMsg = new LatticeMessage(LatticeMessage.Type.DECIDE, slot, state.proposalNumber.get(), state.decidedValue, processId);
            broadcastLatticeMessage(decideMsg);

            writeDecision(slot, state.decidedValue);
        }
    }

    private void writeDecision(int slot, Set<Integer> values) {
        // Write out the decided values for this slot
        decisionLock.lock();
        try {
            // Write the line: space-separated integers
            List<Integer> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            for (int val : sorted) {
                outputWriter.write(val + " ");
            }
            outputWriter.newLine();
            outputWriter.flush();
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            decisionLock.unlock();
        }
    }

    private void sendLatticeMessage(LatticeMessage lMsg, int destId) {
        byte[] payload = serializeLatticeMessage(lMsg);
        Message msg = new Message(processId, createSeqNum(), payload);
        msg.setType("LA");
        pl.send(destId, msg);
    }

    // A simple method to create unique seqNums locally
    // In a real system you'd coordinate or ensure correctness
    private AtomicInteger localSeqGen = new AtomicInteger(100000);
    private int createSeqNum() {
        return localSeqGen.getAndIncrement();
    }

    public void close() {
        try {
            outputWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
