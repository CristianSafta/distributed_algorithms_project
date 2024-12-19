package cs451;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.*;

public class ConfigHandler {

    private short hostId;
    private String outputPath;
    private String configPath;
    private List<NodeAddress> hostsList = new ArrayList<>();

    public void interpretArgs(String[] args) {
        if (args.length != GlobalParams.ARG_LIMIT_CONFIG) {
            showHelp();
        }

        if (!readId(args[GlobalParams.ID_KEY], args[GlobalParams.ID_VALUE])) {
            showHelp();
        }

        if (!readHosts(args[GlobalParams.HOSTS_KEY], args[GlobalParams.HOSTS_VALUE])) {
            showHelp();
        }

        if (!inValidRange(hostId)) {
            showHelp();
        }

        if (!readOutputPath(args[GlobalParams.OUTPUT_KEY], args[GlobalParams.OUTPUT_VALUE])) {
            showHelp();
        }

        if (!readConfigPath(args[GlobalParams.CONFIG_VALUE])) {
            showHelp();
        }
    }

    private boolean readId(String key, String val) {
        if (!"--id".equals(key)) {
            return false;
        }
        try {
            hostId = Short.parseShort(val);
            if (hostId <= 0) {
                System.err.println("Id must be positive!");
                return false;
            }
        } catch (NumberFormatException e) {
            System.err.println("Id must be a number!");
            return false;
        }
        return true;
    }

    private boolean readHosts(String key, String filename) {
        if (!"--hosts".equals(key)) {
            return false;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            int lineNum = 1;
            for (String line; (line = br.readLine()) != null; lineNum++) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length != 3) {
                    System.err.println("Line " + lineNum + " in hosts file is malformed!");
                    return false;
                }
                NodeAddress node = new NodeAddress();
                if (!node.define(parts[0], parts[1], parts[2])) {
                    return false;
                }
                hostsList.add(node);
            }
        } catch (IOException e) {
            System.err.println("Hosts file issue!");
            return false;
        }

        return checkIdRange();
    }

    private boolean readOutputPath(String key, String value) {
        if (!"--output".equals(key)) {
            return false;
        }
        File f = new File(value);
        outputPath = f.getPath();
        return true;
    }

    private boolean readConfigPath(String value) {
        File f = new File(value);
        configPath = f.getPath();
        return true;
    }

    private boolean checkIdRange() {
        int num = hostsList.size();
        for (NodeAddress node : hostsList) {
            if (node.getId() < 1 || node.getId() > num) {
                System.err.println("Host ID out of range!");
                return false;
            }
        }
        // sort hosts by id to keep consistency
        hostsList.sort(Comparator.comparingInt(a -> a.getId()));
        return true;
    }

    private boolean inValidRange(int id) {
        return id <= hostsList.size();
    }

    private void showHelp() {
        System.err.println("Usage: ./run.sh --id ID --hosts HOSTS --output OUTPUT CONFIG");
        System.exit(1);
    }

    public short getMyId() {
        return hostId;
    }

    public List<NodeAddress> getHosts() {
        return hostsList;
    }

    public Map<Short, NodeAddress> getHostsMap() {
        List<NodeAddress> copy = new ArrayList<>(hostsList);
        Collections.shuffle(copy);
        Map<Short, NodeAddress> result = new HashMap<>(copy.size());
        for (NodeAddress h : copy) {
            result.put(h.getId(), h);
        }
        return result;
    }

    public String getOutput() {
        return outputPath;
    }

    public String getConfig() {
        return configPath;
    }
}
