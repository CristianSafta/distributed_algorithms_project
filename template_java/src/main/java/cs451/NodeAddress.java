package cs451;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class NodeAddress {

    private static final String IP_PREFIX = "/";

    private short identifier;
    private String ip;
    private int port = -1;
    private InetSocketAddress socketAddr;

    public boolean define(String idString, String ipString, String portString) {
        try {
            identifier = Short.parseShort(idString);

            String fullIP = InetAddress.getByName(ipString).toString();
            if (fullIP.startsWith(IP_PREFIX)) {
                ip = fullIP.substring(1);
            } else {
                ip = InetAddress.getByName(fullIP.split(IP_PREFIX)[0]).getHostAddress();
            }

            port = Integer.parseInt(portString);
            if (port <= 0) {
                System.err.println("Port must be positive!");
                return false;
            }
        } catch (NumberFormatException e) {
            if (port == -1) {
                System.err.println("Host ID must be a number!");
            } else {
                System.err.println("Port must be a number!");
            }
            return false;
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        this.socketAddr = new InetSocketAddress(resolveInetAddress(), getPortNumber());
        return true;
    }

    public short getId() {
        return identifier;
    }

    public String getIp() {
        return ip;
    }

    private InetAddress resolveInetAddress() {
        try {
            return InetAddress.getByName(this.ip);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.err.println("Unable to parse IP address unexpectedly");
        }
        return null;
    }

    public InetSocketAddress getSocketAddress() {
        return socketAddr;
    }

    public int getPortNumber() {
        return port;
    }

    @Override
    public String toString() {
        return "NodeAddress [id=" + identifier + ", ip=" + ip + ", port=" + port + "]";
    }
}
