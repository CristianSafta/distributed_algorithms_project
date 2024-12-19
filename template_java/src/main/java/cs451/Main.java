package cs451;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        ConfigHandler handler = new ConfigHandler();
        handler.interpretArgs(args);

        long pid = ProcessHandle.current().pid();
        System.out.println("My PID: " + pid);
        System.out.println("Stop with `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "`\n");

        System.out.println("My ID: " + handler.getMyId() + "\n");
        System.out.println("List of hosts:");
        System.out.println("==============");
        for (NodeAddress h : handler.getHosts()) {
            System.out.println(h.getId());
            System.out.println("IP: " + h.getIp());
            System.out.println("Port: " + h.getPortNumber());
            System.out.println();
        }

        System.out.println("Output path:");
        System.out.println("==============");
        System.out.println(handler.getOutput() + "\n");

        System.out.println("Config path:");
        System.out.println("==============");
        System.out.println(handler.getConfig() + "\n");

        System.out.println("Initializing...");

        System.out.println("Creating log file...");
        final RecordKeeper rec = new RecordKeeper(handler.getOutput());
        final short myId = handler.getMyId();
        final Map<Short, NodeAddress> hm = handler.getHostsMap();
        final ConfigurationFileReader cfgReader = new ConfigurationFileReader();
        cfgReader.setConfigPath(handler.getConfig());

        System.out.println("Config content:");
        System.out.println("===============");
        System.out.println(cfgReader.obtainNestedConfig());

        try {
            RequestsInitiator init = new RequestsInitiator(myId, hm, cfgReader);
            ChannelState chSt = init.snapshotChannel();
            ConsensusData.ConsensusStage stage = init.snapshotConsensus();

            ResultsReceiver recv = new ResultsReceiver(rec, myId, hm, cfgReader, chSt, stage);

            Thread initThread = new Thread(init);
            Thread recvThread = new Thread(recv);
            setupSignalHandlers(rec, handler.getOutput(), initThread, recvThread);

            System.out.println("Starting broadcast/delivery");
            initThread.start();
            recvThread.start();
        } catch (UnknownHostException | SocketException e) {
            System.err.println("Host configuration error");
            e.printStackTrace();
            System.exit(1);
        }

        // Wait indefinitely
        while (true) {
            Thread.sleep(3600_000);
        }
    }

    private static void handleStop(RecordKeeper rec, String output, Thread sender, Thread receiver) {
        System.out.println("Stopping network packet handling...");
        sender.interrupt();
        receiver.interrupt();
        System.out.println("Final flush...");
        rec.attemptFlush(true);
    }

    private static void setupSignalHandlers(RecordKeeper rec, String output, Thread sender, Thread receiver) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleStop(rec, output, sender, receiver);
            }
        });
    }
}
