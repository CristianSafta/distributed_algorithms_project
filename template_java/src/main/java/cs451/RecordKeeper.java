package cs451;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RecordKeeper {
    private final AtomicReference<StringBuilder> logs;
    private AtomicLong lastFlush;
    private final String outFile;

    public RecordKeeper(String output) {
        this.logs = new AtomicReference<>(new StringBuilder(""));
        this.lastFlush = new AtomicLong(System.currentTimeMillis());
        this.outFile = output;
        System.out.println("Preparing output file...");
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(output));
            bw.append("");
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize output");
        }
    }

    public synchronized void addLog(String s) {
        synchronized (logs) {
            logs.getAndUpdate(sb -> sb.append(s));
        }
    }

    public synchronized void attemptFlush(boolean force) {
        synchronized (logs) {
            if ((System.currentTimeMillis() - lastFlush.get()) > GlobalParams.TIME_BEFORE_FLUSH || force) {
                System.out.println("Flushing output...");
                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(outFile, true));
                    bw.append(logs.getAndUpdate(str -> new StringBuilder("")));
                    bw.flush();
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Flush failed");
                }
                lastFlush.set(System.currentTimeMillis());
                System.gc();
            }
        }
    }
}
