package cs451;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ConfigurationFileReader {

    private String configPath;

    public boolean setConfigPath(String path) {
        this.configPath = path;
        return true;
    }

    public String getConfigPath() {
        return configPath;
    }

    public NestedConfig obtainNestedConfig() {
        try (BufferedReader rd = new BufferedReader(new FileReader(configPath))) {
            List<String[]> data = new ArrayList<>();
            String line;
            while ((line = rd.readLine()) != null) {
                data.add(line.split(" "));
            }
            if (!data.isEmpty() && data.get(0) != null && data.get(0).length == 3) {
                int p = Integer.parseInt(data.get(0)[0]);
                int vs = Integer.parseInt(data.get(0)[1]);
                int ds = Integer.parseInt(data.get(0)[2]);
                if (data.size() - 1 != p) {
                    throw new IllegalArgumentException("Unexpected number of proposals in config");
                }
                List<Set<Integer>> proposals = new ArrayList<>(p);
                for (int i = 1; i <= p; ++i) {
                    if (data.get(i).length <= vs) {
                        Set<Integer> proposal = new HashSet<>();
                        for (String elem : data.get(i)) {
                            proposal.add(Integer.parseInt(elem));
                        }
                        proposals.add(proposal);
                    } else {
                        throw new IllegalArgumentException("Line " + i + " has more values than expected");
                    }
                }
                return new NestedConfig(p, vs, ds, proposals);
            }
        } catch (NumberFormatException e) {
            System.err.println("Parameter parsing error in config");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("I/O error while reading config");
            e.printStackTrace();
        }
        return null;
    }

    public static class NestedConfig {
        private final int p;
        private final int vs;
        private final int ds;
        private final List<Set<Integer>> props;

        public NestedConfig(int p, int vs, int ds, List<Set<Integer>> props) {
            if (props == null) {
                throw new IllegalArgumentException("Null proposals not allowed");
            }
            if (props.size() != p) {
                throw new IllegalArgumentException("Mismatch between p and proposals count");
            }
            this.p = p;
            this.vs = vs;
            this.ds = ds;
            this.props = props;
        }

        public int getP() {
            return p;
        }

        public int getVs() {
            return vs;
        }

        public int getDs() {
            return ds;
        }

        public List<Set<Integer>> getProposals() {
            return props;
        }

        @Override
        public String toString() {
            return "NestedConfig [p=" + p + ", vs=" + vs + ", ds=" + ds + ", proposals=" + props + "]";
        }
    }
}
