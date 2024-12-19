package cs451;

public interface SharedInterfaces {

    // Replaces Deliverable
    interface MsgReception {
        // delivers a message to this instance
        void receiveDeliveredMessage(NetMessage m);
    }

    // Replaces LatticeStateGiver
    interface ConsensusSnapshot {
        ConsensusData.ConsensusStage snapshotConsensus();
    }

    // Replaces PlStateGiver
    interface ChannelSnapshot {
        ChannelState snapshotChannel();
    }
}

// Replaces ActorType with a different naming
enum ParticipantRole {
    INITIATOR, FOLLOWER
}
