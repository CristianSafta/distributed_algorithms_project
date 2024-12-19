package cs451;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RequestsInitiator implements Runnable, SharedInterfaces.ChannelSnapshot, SharedInterfaces.ConsensusSnapshot {
    private final ConfigurationFileReader.NestedConfig confData;
    private final LatticeAgreement topLevelAgreement;

    public RequestsInitiator(short myId, Map<Short, NodeAddress> mapHosts, ConfigurationFileReader cfg)
            throws SocketException, UnknownHostException {
        this.confData = cfg.obtainNestedConfig();
        this.topLevelAgreement = new LatticeAgreement(myId, mapHosts, confData);
    }

    @Override
    public void run() {
        Thread consensusSender = new Thread(topLevelAgreement, "Consensus Sender");
        consensusSender.start();
        try {
            List<Set<Integer>> proposals = confData.getProposals();
            for (int i = 0; i < proposals.size(); ++i) {
                Set<Integer> prop = proposals.get(i);
                while (!topLevelAgreement.submitProposal(i, prop)) {
                    Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
                }
            }
        } catch (InterruptedException e) {
            System.err.println("RequestsInitiator interrupted");
            consensusSender.interrupt();
        }
    }

    @Override
    public ChannelState snapshotChannel() {
        return topLevelAgreement.snapshotChannel();
    }

    @Override
    public ConsensusData.ConsensusStage snapshotConsensus() {
        return topLevelAgreement.snapshotConsensus();
    }
}
