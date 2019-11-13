package io.atomix.protocols.raft.storage.snapshot;

import io.atomix.protocols.raft.storage.RaftStorage;

@FunctionalInterface
public interface SnapshotStoreFactory {
  SnapshotStore createSnapshotStore(RaftStorage storage);
}
