package cs451;

import java.io.*;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static int m; // Number of messages to send
    private static BufferedWriter logWriter;

    private static void readConfig(String configPath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(configPath));
        String line = reader.readLine();
        String[] tokens = line.trim().split(" ");
        m = Integer.parseInt(tokens[0]);
        reader.close();
    }

    private static void initLog(String outputPath) {
        try {
            logWriter = new BufferedWriter(new FileWriter(outputPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void logEvent(String event) {
        try {
            logWriter.write(event);
            logWriter.newLine();
            logWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void closeLog() {
        try {
            if (logWriter != null) {
                logWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleSignal() {
        // Handle termination signals if necessary
        System.out.println("Signal received. Shutting down.");
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::handleSignal));
    }

    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        initLog(parser.output());

        // Read the config file
        try {
            readConfig(parser.config());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        int processId = parser.myId();
        List<Host> hostsList = parser.hosts();
        Map<Integer, Host> hosts = new HashMap<>();
        for (Host host : hostsList) {
            hosts.put(host.getId(), host);
        }

        Host myHost = hosts.get(processId);

        PerfectLinks perfectLinks;
        try {
            perfectLinks = new PerfectLinks(processId, myHost.getPort(), hosts);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Initialize URB
        UniformReliableBroadcast urb = new UniformReliableBroadcast(perfectLinks, processId, hosts);

        // Initialize FIFO Broadcast
        FIFOBroadcast fifoBroadcast = new FIFOBroadcast(urb, processId);

        // Start the PerfectLinks receiver
        perfectLinks.startReceiver();

        System.out.println("Broadcasting and delivering messages...\n");

        // Sender process
        int localSeqNum = 0;
        for (int seqNum = 1; seqNum <= m; seqNum++) {
            localSeqNum += 1;
            String payload = ""; // Payload is optional
            Message message = new Message(processId, localSeqNum, payload.getBytes());
            message.setType("FRB");
            fifoBroadcast.frbBroadcast(message);

            // Log the broadcasting event
            logEvent("b " + localSeqNum);

            if ((localSeqNum % 1000) == 0) {
                System.out.println("Broadcasted message SeqNum " + localSeqNum);
            }
        }

        // Wait for termination signal
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }

        // Close resources
        perfectLinks.close();
        closeLog();
    }
}
