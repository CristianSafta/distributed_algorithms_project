package cs451;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ResultsReceiver implements Runnable, SharedInterfaces.MsgReception {

    private final RecordKeeper rec;
    private final ConfigurationFileReader.NestedConfig nestCfg;
    private final ConcurrentSkipListMap<Integer, Set<Integer>> deliverables = new ConcurrentSkipListMap<>();
    private final LatticeAgreement consensusModule;
    private final AtomicInteger nextDeliverIndex = new AtomicInteger(0);

    public ResultsReceiver(RecordKeeper logger, short id, Map<Short, NodeAddress> hostsMap,
                           ConfigurationFileReader cfgReader, ChannelState plSt,
                           ConsensusData.ConsensusStage latticeSt)
            throws UnknownHostException, SocketException {
        this.rec = logger;
        this.nestCfg = cfgReader.obtainNestedConfig();
        this.consensusModule = new LatticeAgreement(id, hostsMap, this, nestCfg, latticeSt, plSt);
    }

    public ResultsReceiver() {
        this.rec = null;
        this.nestCfg = null;
        this.consensusModule = null;
    }

    public void deliver(int agreementId, Set<Integer> values) {
        if (values == null) {
            throw new IllegalArgumentException("Null delivery");
        }
        deliverables.put(agreementId, Collections.unmodifiableSet(values));
    }

    @Override
    public void run() {
        Thread latticeThread = new Thread(consensusModule, "Consensus Receiver");
        latticeThread.start();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int idx = nextDeliverIndex.get();
                if (deliverables.containsKey(idx)) {
                    StringBuilder sb = new StringBuilder();
                    for (int v : deliverables.get(idx)) {
                        sb.append(v).append(" ");
                    }
                    sb.append("\n");
                    rec.addLog(sb.toString());
                    nextDeliverIndex.incrementAndGet();
                    deliverables.remove(idx);
                } else {
                    Thread.sleep(GlobalParams.SLEEP_BEFORE_NEXT_POLL);
                }
                rec.attemptFlush(false);
            }
        } catch (InterruptedException e) {
            System.err.println("ResultsReceiver interrupted");
            latticeThread.interrupt();
        }
    }

    @Override
    public void receiveDeliveredMessage(NetMessage m) {
        // This is the deliver method from the original interface
        consensusModule.receiveDeliveredMessage(m);
    }
}
