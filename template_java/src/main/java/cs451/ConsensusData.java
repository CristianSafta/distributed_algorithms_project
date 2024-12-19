package cs451;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * This class merges the original logic of AgreementState and LatticeState into a single holder.
 * DecisionStatus stores per-agreement state (original AgreementState),
 * while ConsensusStage (inner class) manages global concurrency structures (original LatticeState).
 */
public class ConsensusData {

    // Renamed from AgreementState to DecisionStatus
    public static class DecisionStatus {
        // Tracks if we are still active on this agreement
        private final AtomicBoolean activeFlag;
        private final AtomicInteger acks;
        private final AtomicInteger nacks;
        private final AtomicInteger decidedCount;
        private final AtomicInteger currentProposalNum;
        private final ConcurrentHashMap.KeySetView<Integer, Boolean> proposedSet;
        private final ConcurrentHashMap.KeySetView<Integer, Boolean> acceptedSet;

        public DecisionStatus(int proposalNum, Set<Integer> proposedVals) {
            this.activeFlag = new AtomicBoolean(true);
            this.acks = new AtomicInteger(0);
            this.nacks = new AtomicInteger(0);
            this.decidedCount = new AtomicInteger(0);
            this.currentProposalNum = new AtomicInteger(proposalNum);
            this.proposedSet = ConcurrentHashMap.newKeySet(proposedVals.size());
            for (int v : proposedVals) {
                this.proposedSet.add(v);
            }
            this.acceptedSet = ConcurrentHashMap.newKeySet(proposedVals.size());
        }

        public synchronized void redefineProposals(Set<Integer> newValues) {
            this.proposedSet.clear();
            this.proposedSet.addAll(newValues);
        }

        public synchronized void redefineAccepted(Set<Integer> newAccepted) {
            this.acceptedSet.clear();
            this.acceptedSet.addAll(newAccepted);
        }

        public Set<Integer> viewProposed() {
            return Collections.unmodifiableSet(this.proposedSet);
        }

        public Set<Integer> viewAccepted() {
            return Collections.unmodifiableSet(this.acceptedSet);
        }

        public synchronized boolean expandProposed(Set<Integer> moreProposed) {
            return this.proposedSet.addAll(Collections.unmodifiableSet(moreProposed));
        }

        public synchronized boolean expandAccepted(Set<Integer> moreAccepted) {
            return this.acceptedSet.addAll(Collections.unmodifiableSet(moreAccepted));
        }

        public synchronized boolean acceptedSubsetOf(Set<Integer> proposed) {
            return proposed.containsAll(acceptedSet);
        }

        public boolean isActive() {
            return activeFlag.get();
        }

        public void deactivate() {
            activeFlag.set(false);
        }

        public int readAcks() {
            return acks.get();
        }

        public int readNacks() {
            return nacks.get();
        }

        public void clearAcks() {
            acks.set(0);
        }

        public void clearNacks() {
            nacks.set(0);
        }

        public synchronized void incAcks() {
            acks.incrementAndGet();
        }

        public synchronized void incNacks() {
            nacks.incrementAndGet();
        }

        public int getDecisionsCount() {
            return decidedCount.get();
        }

        public synchronized void incDecisions() {
            decidedCount.incrementAndGet();
        }

        public int currentProposalNumber() {
            return currentProposalNum.get();
        }

        public synchronized void bumpProposalNumber() {
            currentProposalNum.incrementAndGet();
        }

        @Override
        public String toString() {
            return "DecisionStatus [active=" + activeFlag + ", ackCount=" + acks + ", nackCount=" + nacks
                    + ", decidedCount=" + decidedCount + ", currentPropNum=" + currentProposalNum
                    + ", proposedVals=" + proposedSet + ", acceptedVals=" + acceptedSet + "]";
        }
    }

    // Replaces LatticeState (renamed to ConsensusStage)
    public static class ConsensusStage {
        private final ConcurrentSkipListMap<Integer, DecisionStatus> allAgreements;
        private final AtomicInteger winSize;
        private final AtomicInteger bottomWindow;
        private final ConcurrentLinkedQueue<NetMessage> queueToBroadcast;
        private final ConcurrentLinkedQueue<NetMessage> queueToDeliver;

        public ConsensusStage(ConcurrentSkipListMap<Integer, DecisionStatus> agreements,
                              AtomicInteger windowSize,
                              AtomicInteger windowBottom,
                              ConcurrentLinkedQueue<NetMessage> toBroadcast,
                              ConcurrentLinkedQueue<NetMessage> toDeliver) {
            this.allAgreements = agreements;
            this.winSize = windowSize;
            this.bottomWindow = windowBottom;
            this.queueToBroadcast = toBroadcast;
            this.queueToDeliver = toDeliver;
        }

        public ConcurrentSkipListMap<Integer, DecisionStatus> getAgreements() {
            return allAgreements;
        }

        public AtomicInteger getWindowSize() {
            return winSize;
        }

        public AtomicInteger getWindowBottom() {
            return bottomWindow;
        }

        public ConcurrentLinkedQueue<NetMessage> getToBroadcast() {
            return queueToBroadcast;
        }

        public ConcurrentLinkedQueue<NetMessage> getToDeliver() {
            return queueToDeliver;
        }
    }

}
