package cs451;

import java.io.*;
import java.net.SocketException;
import java.util.*;

public class Main {

    private static int p;  // number of proposals (slots)
    private static int vs; // max size of each proposal
    private static int ds; // max number of distinct elements across proposals
    private static List<Set<Integer>> slotProposals;
    private static BufferedWriter logWriter;

    // Modified readConfig to read p, vs, ds, and proposals for each slot
    private static void readConfig(String configPath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(configPath));
        String line = reader.readLine();
        String[] tokens = line.trim().split(" ");
        p = Integer.parseInt(tokens[0]);
        vs = Integer.parseInt(tokens[1]);
        ds = Integer.parseInt(tokens[2]);

        slotProposals = new ArrayList<>();

        for (int i = 0; i < p; i++) {
            line = reader.readLine();
            if (line == null) {
                // If fewer lines than p, just assume empty sets for missing slots
                slotProposals.add(new HashSet<>());
                continue;
            }

            String[] valTokens = line.trim().split(" ");
            Set<Integer> proposalSet = new HashSet<>();
            for (String valStr : valTokens) {
                proposalSet.add(Integer.parseInt(valStr));
            }
            slotProposals.add(proposalSet);
        }

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

        // Read the config file (p, vs, ds, and proposals)
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

        // Start PerfectLinks receiver
        perfectLinks.startReceiver();

        // Initialize LatticeAgreement for this milestone
        // Output will be directly written by LatticeAgreement. We pass parser.output() as output path.
        LatticeAgreement la = new LatticeAgreement(processId, hosts, perfectLinks, p, parser.output());

        System.out.println("Starting Lattice Agreement...\n");

        // Propose each slot
        for (int slot = 0; slot < p; slot++) {
            Set<Integer> proposal = slotProposals.get(slot);
            la.propose(slot, proposal);
        }

        // Wait for termination signal
        // In a robust solution, you'd check if all slots decided before exiting.
        // For now, we mimic Milestone 2 behavior and wait indefinitely or until a signal arrives.
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }

        // Close resources
        perfectLinks.close();
        la.close(); // Ensure we flush any pending decisions
        closeLog();
    }
}
