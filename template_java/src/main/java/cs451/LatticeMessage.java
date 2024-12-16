package cs451;

import java.io.Serializable;
import java.util.Set;
import java.util.HashSet;

public class LatticeMessage implements Serializable {
    public enum Type {
        PROPOSE, ACK, NACK, DECIDE
    }

    private Type type;
    private int slot;
    private int proposalNumber;
    private Set<Integer> values; // The lattice set
    private int senderId; // To identify who sent it

    public LatticeMessage(Type type, int slot, int proposalNumber, Set<Integer> values, int senderId) {
        this.type = type;
        this.slot = slot;
        this.proposalNumber = proposalNumber;
        this.values = values;
        this.senderId = senderId;
    }

    public Type getType() { return type; }
    public int getSlot() { return slot; }
    public int getProposalNumber() { return proposalNumber; }
    public Set<Integer> getValues() { return values; }
    public int getSenderId() { return senderId; }
}
