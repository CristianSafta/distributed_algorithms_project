package cs451;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LatticeAgreement implements SharedInterfaces.ChannelSnapshot, SharedInterfaces.ConsensusSnapshot, SharedInterfaces.MsgReception, Runnable {
    private final short myself;
    private final Map<Short, NodeAddress> nodeIndex;
    private final ConcurrentSkipListMap<Integer, ConsensusData.DecisionStatus> allDecisions;
    private final ConcurrentLinkedQueue<NetMessage> forBroadcast;
    private final ConcurrentLinkedQueue<NetMessage> incomingDelivery;
    private final AtomicInteger sizeWindow;
    private final AtomicInteger bottomWindow;
    private final PerfectLinks channel;
    private final ParticipantRole role;
    private final ResultsReceiver parentRef;

    public LatticeAgreement(short id, Map<Short, NodeAddress> map, ConfigurationFileReader.NestedConfig config)
            throws SocketException, UnknownHostException {
        this.myself = id;
        this.nodeIndex = map;
        this.allDecisions = new ConcurrentSkipListMap<>();
        this.sizeWindow = new AtomicInteger(0);
        this.bottomWindow = new AtomicInteger(0);
        this.forBroadcast = new ConcurrentLinkedQueue<>();
        this.channel = new PerfectLinks(myself, nodeIndex, config);
        this.role = ParticipantRole.INITIATOR;
        this.incomingDelivery = new ConcurrentLinkedQueue<>();
        this.parentRef = null;
    }

    public LatticeAgreement(short id, Map<Short, NodeAddress> map, ResultsReceiver parent,
                            ConfigurationFileReader.NestedConfig config,
                            ConsensusData.ConsensusStage cStage, ChannelState cSt) {
        this.myself = id;
        this.nodeIndex = map;
        this.allDecisions = cStage.getAgreements();
        this.sizeWindow = cStage.getWindowSize();
        this.bottomWindow = cStage.getWindowBottom();
        this.forBroadcast = cStage.getToBroadcast();
        this.incomingDelivery = cStage.getToDeliver();
        this.channel = new PerfectLinks(myself, nodeIndex, this, config, cSt);
        this.role = ParticipantRole.FOLLOWER;
        this.parentRef = parent;
    }

    public boolean submitProposal(int agreementId, Set<Integer> values) {
        if (values == null) {
            throw new IllegalArgumentException("Cannot propose null set");
        }
        if (!allDecisions.containsKey(agreementId) && sizeWindow.get() > GlobalParams.MAX_OUT_OF_ORDER_DELIVERY) {
            // Too many in-flight
            return false;
        } else {
            if (allDecisions.putIfAbsent(agreementId, new ConsensusData.DecisionStatus(0, Collections.emptySet())) == null) {
                sizeWindow.incrementAndGet();
            }
            synchronized (allDecisions) {
                ConsensusData.DecisionStatus ds = allDecisions.get(agreementId);
                ds.redefineProposals(values);
                ds.bumpProposalNumber();
                return forBroadcast.add(new NetMessage(NetMessage.EchoMarker.ECHOSTR, myself, myself, agreementId,
                        ds.currentProposalNumber(), NetMessage.PayLoadKind.PROPOSAL, values));
            }
        }
    }

    @Override
    public ChannelState snapshotChannel() {
        return channel.snapshotChannel();
    }

    @Override
    public void receiveDeliveredMessage(NetMessage m) {
        incomingDelivery.add(m);
    }

    @Override
    public ConsensusData.ConsensusStage snapshotConsensus() {
        return new ConsensusData.ConsensusStage(allDecisions, sizeWindow, bottomWindow, forBroadcast, incomingDelivery);
    }

    @Override
    public void run() {
        Thread linkThread = new Thread(channel, "Host " + myself + " " + role.name());
        linkThread.start();
        try {
            if (role == ParticipantRole.INITIATOR) {
                while (!Thread.currentThread().isInterrupted()) {
                    handleInitiatorLoop();
                }
            } else if (role == ParticipantRole.FOLLOWER) {
                while (!Thread.currentThread().isInterrupted()) {
                    handleFollowerLoop();
                }
            } else {
                throw new IllegalStateException("Unknown role");
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted " + role.name() + " LayeredConsensus");
            linkThread.interrupt();
        }
    }

    private void handleInitiatorLoop() throws InterruptedException {
        NetMessage toSend = forBroadcast.poll();
        if (toSend == null) {
            Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
        } else {
            for (short d : nodeIndex.keySet()) {
                while (!channel.scheduleToSend(toSend, d)) {
                    Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
                }
            }
        }
    }

    private void handleFollowerLoop() throws InterruptedException {
        NetMessage delivered = incomingDelivery.poll();
        if (delivered == null) {
            Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
        } else {
            int agId = delivered.getAgreementId();
            if (bottomWindow.get() > agId) {
                return; // we moved past this agreement
            }
            synchronized (allDecisions) {
                ConsensusData.DecisionStatus ds = allDecisions.get(agId);
                if (delivered.getPayloadType() == NetMessage.PayLoadKind.ACK) {
                    if (!allDecisions.containsKey(agId)) {
                        System.err.println("Received ACK for unknown agreement: " + agId);
                        return;
                    }
                    if (ds.currentProposalNumber() == delivered.getActivePropNumber()) {
                        ds.incAcks();
                    }
                } else if (delivered.getPayloadType() == NetMessage.PayLoadKind.NACK) {
                    if (!allDecisions.containsKey(agId)) {
                        System.err.println("NACK for unknown agreement: " + agId);
                        return;
                    }
                    if (ds.currentProposalNumber() == delivered.getActivePropNumber()) {
                        ds.expandProposed(delivered.getVals());
                        ds.incNacks();
                    }
                } else if (delivered.getPayloadType() == NetMessage.PayLoadKind.DECIDED) {
                    if (allDecisions.putIfAbsent(agId, new ConsensusData.DecisionStatus(delivered.getActivePropNumber(), Collections.emptySet())) == null) {
                        sizeWindow.incrementAndGet();
                    }
                    ds = allDecisions.get(agId);
                    ds.incDecisions();
                } else if (delivered.getPayloadType() == NetMessage.PayLoadKind.PROPOSAL) {
                    if (allDecisions.putIfAbsent(agId, new ConsensusData.DecisionStatus(delivered.getActivePropNumber(), Collections.emptySet())) == null) {
                        sizeWindow.incrementAndGet();
                    }
                    ds = allDecisions.get(agId);
                    if (ds.acceptedSubsetOf(delivered.getVals())) {
                        ds.redefineAccepted(delivered.getVals());
                        while (!channel.scheduleToSend(
                                new NetMessage(NetMessage.EchoMarker.ECHOSTR, myself, myself, agId, delivered.getActivePropNumber(),
                                        NetMessage.PayLoadKind.ACK, null),
                                delivered.getSourceId())) {
                            Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
                        }
                    } else {
                        ds.expandAccepted(delivered.getVals());
                        while (!channel.scheduleToSend(
                                new NetMessage(NetMessage.EchoMarker.ECHOSTR, myself, myself, agId, delivered.getActivePropNumber(),
                                        NetMessage.PayLoadKind.NACK, ds.viewAccepted()),
                                delivered.getSourceId())) {
                            Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
                        }
                    }
                } else {
                    throw new IllegalStateException("Unknown payload");
                }

                if (ds.isActive()) {
                    if (ds.readNacks() > 0 && ds.readAcks() + ds.readNacks() > nodeIndex.size() / 2) {
                        ds.bumpProposalNumber();
                        ds.clearAcks();
                        ds.clearNacks();
                        forBroadcast.add(new NetMessage(NetMessage.EchoMarker.ECHOSTR, myself, myself, agId, ds.currentProposalNumber(),
                                NetMessage.PayLoadKind.PROPOSAL, ds.viewProposed()));
                    }
                    if (ds.readAcks() > nodeIndex.size() / 2) {
                        parentRef.deliver(agId, ds.viewProposed());
                        ds.deactivate();
                        sizeWindow.decrementAndGet();
                        forBroadcast.add(new NetMessage(NetMessage.EchoMarker.ECHOSTR, myself, myself, agId,
                                ds.currentProposalNumber(), NetMessage.PayLoadKind.DECIDED, null));
                    }
                }

                if (allDecisions.firstKey() == bottomWindow.get()
                        && allDecisions.firstEntry().getValue().getDecisionsCount() >= nodeIndex.size()) {
                    bottomWindow.incrementAndGet();
                    channel.flush(allDecisions.firstKey());
                    allDecisions.remove(allDecisions.firstKey());
                }
            }
        }
    }
}
