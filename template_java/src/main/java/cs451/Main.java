package cs451;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static int m; // Number of messages to send
    private static int receiverId; // Receiver process ID
    private static BufferedWriter logWriter;

    private static void readConfig(String configPath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(configPath));
        String line = reader.readLine();
        String[] tokens = line.trim().split(" ");
        m = Integer.parseInt(tokens[0]);
        receiverId = Integer.parseInt(tokens[1]);
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
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        //write/flush output file if necessary
        System.out.println("Writing output.");
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal();
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        // example
        long pid = ProcessHandle.current().pid();
        System.out.println("My PID: " + pid + "\n");
        System.out.println("From a new terminal type `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "` to stop processing packets\n");

        System.out.println("My ID: " + parser.myId() + "\n");
        System.out.println("List of resolved hosts is:");
        System.out.println("==========================");
        for (Host host: parser.hosts()) {
            System.out.println(host.getId());
            System.out.println("Human-readable IP: " + host.getIp());
            System.out.println("Human-readable Port: " + host.getPort());
            System.out.println();
        }
        System.out.println();

        System.out.println("Path to output:");
        System.out.println("===============");
        System.out.println(parser.output() + "\n");

        System.out.println("Path to config:");
        System.out.println("===============");
        System.out.println(parser.config() + "\n");

        System.out.println("Doing some initialization\n");
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

        // Start the receiver thread
        perfectLinks.startReceiver();

        System.out.println("Broadcasting and delivering messages...\n");

        // After a process finishes broadcasting,
        // it waits forever for the delivery of messages.
        /*while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }

         */

        // Determine if this process is the sender or receiver
        if (processId != receiverId) {
            // Sender process
            for (int seqNum = 1; seqNum <= m; seqNum++) {
                String payload = ""; // Empty payload as per project
                Message message = new Message(processId, seqNum, payload.getBytes());
                perfectLinks.send(receiverId, message);

                // Log the sending event
                logEvent("b " + seqNum);
            }
        } else {
            // Receiver process
            // No action needed; delivery happens in the PerfectLinks class
            // Implement logging in the deliver method
        }

        // Wait for termination signal
        while (true) {
            // The process should keep running until it receives a termination signal
            // Signal handling is managed in the template
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
