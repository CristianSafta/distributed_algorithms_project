package cs451;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PerfectLinks {
    private final int processId;
    private final int port;
    private final Map<Integer, Host> hosts;
    private final DatagramSocket socket;
    private final Set<String> deliveredMessages = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> ackedMessages = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Message> pendingMessages = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(8); // Adjust as needed

    public PerfectLinks(int processId, int port, Map<Integer, Host> hosts) throws SocketException {
        this.processId = processId;
        this.port = port;
        this.hosts = hosts;
        //this.socket = new DatagramSocket(port);
        // Create a DatagramSocket with the SO_REUSEADDR option
        DatagramSocket newSocket = new DatagramSocket(null); // Creates an unbound DatagramSocket

        // Allow the socket to reuse the address
        newSocket.setReuseAddress(true);

        // Bind the socket to the port
        newSocket.bind(new InetSocketAddress(port));
        this.socket = newSocket;


    }

    // Start the receiver thread
    public void startReceiver() {
        new Thread(() -> {
            try {
                receive();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Send a message to a specific host
    public void send(int destinationId, Message message) {
        Host destinationHost = hosts.get(destinationId);
        String messageId = message.getSenderId() + "-" + message.getSeqNum();

        pendingMessages.put(messageId, message);

        // Start retransmission task
        executorService.submit(() -> {
            try {
                while (!ackedMessages.contains(messageId)) {
                    sendMessage(destinationHost, message);
                    Thread.sleep(100); // Retransmit every 100ms
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Send the actual UDP message
    private void sendMessage(Host host, Message message) throws IOException {
        InetAddress address = InetAddress.getByName(host.getIp());
        int destPort = host.getPort();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream outStream = new ObjectOutputStream(byteStream);
        outStream.writeObject(message);
        byte[] buffer = byteStream.toByteArray();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, destPort);
        socket.send(packet);
    }

    // Receive messages and ACKs
    private void receive() throws IOException, ClassNotFoundException {
        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
            ObjectInputStream inStream = new ObjectInputStream(byteStream);
            Object receivedObject = inStream.readObject();

            if (receivedObject instanceof Message) {
                Message message = (Message) receivedObject;
                String messageId = message.getSenderId() + "-" + message.getSeqNum();

                // Send ACK back
                sendAck(packet.getAddress(), packet.getPort(), messageId);

                // Deliver the message if not already delivered
                if (!deliveredMessages.contains(messageId)) {
                    deliveredMessages.add(messageId);
                    // Handle delivery (e.g., log it)
                    deliver(message);
                }
            } else if (receivedObject instanceof Ack) {
                Ack ack = (Ack) receivedObject;
                ackedMessages.add(ack.getMessageId());
            }
        }
    }

    // Send ACK
    private void sendAck(InetAddress address, int port, String messageId) throws IOException {
        Ack ack = new Ack(messageId);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream outStream = new ObjectOutputStream(byteStream);
        outStream.writeObject(ack);
        byte[] buffer = byteStream.toByteArray();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }

    // Deliver the message (to be implemented)
    private void deliver(Message message) {
        // Implement delivery logic, e.g., add to a queue or process immediately
        // For now, we'll just print it
        String logEntry = "d " + message.getSenderId() + " " + message.getSeqNum();
        Main.logEvent(logEntry);
        if((message.getSeqNum() % 1000) == 0){
            System.out.println("Delivered message from Process " + message.getSenderId() + ": SeqNum " + message.getSeqNum());
        }
    }

    // Close resources
    public void close() {
        socket.close();
        executorService.shutdown();
    }
}
