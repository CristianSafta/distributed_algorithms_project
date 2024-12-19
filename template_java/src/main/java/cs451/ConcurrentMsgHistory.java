package cs451;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentMsgHistory {
    private final ConcurrentHashMap<Integer, ConcurrentHashMap.KeySetView<Integer, Boolean>> msgMemory;
    private final AtomicInteger flushedMarker = new AtomicInteger(0);
    private final int valueSpace;

    public ConcurrentMsgHistory(int p, int vs) {
        this.msgMemory = new ConcurrentHashMap<>(p);
        this.valueSpace = vs;
    }

    public boolean record(NetMessage.NetCarrier e) {
        if (e.getMessage().getAgreementId() < flushedMarker.get()) {
            return false;
        }
        boolean newlyAdded = msgMemory.putIfAbsent(e.getMessage().getAgreementId(),
                ConcurrentHashMap.newKeySet(valueSpace)) == null;
        ConcurrentHashMap.KeySetView<Integer, Boolean> currentSet = msgMemory.get(e.getMessage().getAgreementId());
        newlyAdded |= currentSet != null && currentSet.add(e.hashCode());
        return newlyAdded;
    }

    public boolean known(NetMessage.NetCarrier e) {
        ConcurrentHashMap.KeySetView<Integer, Boolean> existing = msgMemory.get(e.getMessage().getAgreementId());
        return e.getMessage().getAgreementId() < flushedMarker.get()
                || (existing != null && existing.contains(e.hashCode()));
    }

    public synchronized void flush(int agreementId) {
        msgMemory.entrySet().removeIf(entry -> entry.getKey() < agreementId);
        flushedMarker.compareAndSet(Integer.max(flushedMarker.get(), agreementId), agreementId);
    }

    @Override
    public String toString() {
        return "ConcurrentMsgHistory [messages=" + msgMemory + ", vs=" + valueSpace + "]";
    }
}
