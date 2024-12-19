package cs451;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class NetMessage {

    public enum EchoMarker {
        ECHOSTR, ACKSTR;
        public static EchoMarker[] vals = values();

        public static EchoMarker decode(byte b) {
            if (b < 0 || b >= vals.length) {
                throw new IllegalStateException("Deserialization error: EchoMarker");
            }
            return vals[b];
        }

        public byte encode() {
            if (this.ordinal() > Byte.MAX_VALUE) {
                throw new IllegalStateException("Too many payload types");
            }
            return (byte) this.ordinal();
        }
    }

    public enum PayLoadKind {
        PROPOSAL, ACK, NACK, DECIDED;

        public static PayLoadKind[] vals = values();

        public static PayLoadKind decode(byte b) {
            if (b < 0 || b >= vals.length) {
                throw new IllegalStateException("Deserialization error: PayloadType");
            }
            return vals[b];
        }

        public byte encode() {
            if (this.ordinal() > Byte.MAX_VALUE) {
                throw new IllegalStateException("Too many payload kinds");
            }
            return (byte) this.ordinal();
        }
    }

    public static NetMessage deserialize(byte[] data) {
        ByteBuffer buff = ByteBuffer.wrap(data);
        EchoMarker mk = EchoMarker.decode(buff.get());
        short sender = buff.getShort();
        short origin = buff.getShort();
        int agreeId = buff.getInt();
        int activeNum = buff.getInt();
        PayLoadKind loadKind = PayLoadKind.decode(buff.get());
        if (mk == EchoMarker.ACKSTR || loadKind == PayLoadKind.ACK || loadKind == PayLoadKind.DECIDED) {
            return new NetMessage(mk, sender, origin, agreeId, activeNum, loadKind, null);
        } else {
            int size = buff.getInt();
            HashSet<Integer> proposals = new HashSet<>(size);
            for (int i = 0; i < size; i++) {
                proposals.add(buff.getInt());
            }
            return new NetMessage(mk, sender, origin, agreeId, activeNum, loadKind, proposals);
        }
    }

    private final EchoMarker echoType;
    private final short senderId;
    private final short sourceId;
    private final int agreementId;
    private final int activePropNum;
    private final PayLoadKind payType;
    private final Set<Integer> values;

    public NetMessage(EchoMarker mk, short sid, short soid, int agId, int propNum, PayLoadKind pt, Set<Integer> vals) {
        if (mk == null || pt == null) {
            throw new IllegalArgumentException("Null fields in message");
        }
        if (sid == 0) {
            throw new IllegalArgumentException("Invalid senderId");
        }
        this.echoType = mk;
        this.senderId = sid;
        this.sourceId = soid;
        this.agreementId = agId;
        this.activePropNum = propNum;
        this.payType = pt;
        if (echoType == EchoMarker.ACKSTR || payType == PayLoadKind.ACK || payType == PayLoadKind.DECIDED) {
            this.values = null;
        } else {
            if (vals == null) {
                throw new IllegalArgumentException("Proposal values cannot be null");
            }
            this.values = vals;
        }
    }

    public boolean isPureAck() {
        return echoType == EchoMarker.ACKSTR;
    }

    public boolean matchesAck(NetMessage m) {
        return isPureAck() && this.equals(m);
    }

    public NetMessage ackReply(short ackSender) {
        return new NetMessage(EchoMarker.ACKSTR, ackSender, sourceId, agreementId, activePropNum, payType, null);
    }

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(GlobalParams.MSG_SIZE_NO_VALUES + Integer.BYTES * (values == null ? 0 : values.size() + 1));
        buffer.put(echoType.encode()).putShort(senderId).putShort(sourceId).putInt(agreementId)
                .putInt(activePropNum).put(payType.encode());
        if (!(echoType == EchoMarker.ACKSTR || payType == PayLoadKind.ACK || payType == PayLoadKind.DECIDED)) {
            buffer.putInt(values.size());
            for (int v : values) {
                buffer.putInt(v);
            }
        }
        return buffer.array();
    }

    @Override
    public String toString() {
        return "NetMessage [echoType=" + echoType + ", senderId=" + senderId + ", sourceId=" + sourceId
                + ", agreementId=" + agreementId + ", activePropNumber=" + activePropNum + ", payloadType="
                + payType + ", vals=" + values + "]";
    }

    public EchoMarker getEchoMarker() {
        return echoType;
    }

    public short getSenderId() {
        return senderId;
    }

    public short getSourceId() {
        return sourceId;
    }

    public int getAgreementId() {
        return agreementId;
    }

    public int getActivePropNumber() {
        return activePropNum;
    }

    public PayLoadKind getPayloadType() {
        return payType;
    }

    public Set<Integer> getVals() {
        return values;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + sourceId;
        result = prime * result + agreementId;
        result = prime * result + activePropNum;
        result = prime * result + ((payType == null) ? 0 : payType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NetMessage)) return false;
        NetMessage other = (NetMessage) obj;
        if (sourceId != other.sourceId) return false;
        if (agreementId != other.agreementId) return false;
        if (activePropNum != other.activePropNum) return false;
        if (payType != other.payType) return false;
        return true;
    }

    // Replaces MessageToBeSent with NetCarrier as inner class
    public class NetCarrier {
        private final NetMessage message;
        private final byte[] serializedMsg;
        private final short destId;
        private long sendTime;
        private long retryTimeout;

        public NetCarrier(NetMessage m, short dest, boolean buildFull) {
            if (m == null) {
                throw new IllegalArgumentException("Cannot build NetCarrier with null message");
            }
            if (buildFull) {
                byte[] msgBytes = m.serialize();
                if (msgBytes == null) {
                    throw new IllegalArgumentException("Failed to serialize");
                }
                this.serializedMsg = msgBytes;
            } else {
                this.serializedMsg = null;
            }
            this.destId = dest;
            this.message = m;
        }

        public NetMessage getMessage() {
            return message;
        }

        public byte[] getSerializedMsg() {
            return serializedMsg;
        }

        public short getDest() {
            return destId;
        }

        public long getTimeOfSending() {
            return sendTime;
        }

        public long getTimeout() {
            return retryTimeout;
        }

        public void setTimeOfSending(long t) {
            this.sendTime = t;
        }

        public void setTimeout(long to) {
            this.retryTimeout = to;
        }

        @Override
        public String toString() {
            return "NetCarrier [message=" + message + ", dest=" + destId + "]";
        }

        @Override
        public int hashCode() {
            int prime = 31;
            int result = 1;
            result = prime * result + ((message == null) ? 0 : message.hashCode());
            result = prime * result + destId;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            NetCarrier other = (NetCarrier) obj;
            if (message == null) {
                if (other.message != null)
                    return false;
            } else if (!message.equals(other.message))
                return false;
            return destId == other.destId;
        }
    }

    public NetCarrier toSend(short destination, boolean fullBuild) {
        return new NetCarrier(this, destination, fullBuild);
    }

}
