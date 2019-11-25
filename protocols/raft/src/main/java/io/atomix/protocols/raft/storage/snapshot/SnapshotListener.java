package io.atomix.protocols.raft.storage.snapshot;

/**
 * A snapshot listener is called whenever a new snapshot is committed to a {@link SnapshotStore}.
 */
@FunctionalInterface
public interface SnapshotListener {

  /**
   * Called whenever a new snapshot is committed to the snapshot store.
   *
   * @param store the snapshot store to which it was added
   * @param snapshot the newly committed snapshot
   */
  void onNewSnapshot(Snapshot snapshot, SnapshotStore store);
}
