package io.atomix.protocols.raft.storage.snapshot;

import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.utils.time.WallClockTimestamp;

/**
 * Persists server snapshots via the {@link RaftStorage} module.
 *
 * <p>The server snapshot store is responsible for persisting periodic state machine snapshots
 * according to the configured {@link RaftStorage#storageLevel() storage level}. Each server with a
 * snapshottable state machine persists the state machine state to allow commands to be removed from
 * disk.
 *
 * <p>When a snapshot store is {@link RaftStorage#getSnapshotStore() created} with a non-memory
 * storage level, the store will load any existing snapshots from disk and make them available for
 * reading. Only snapshots that have been written and {@link Snapshot#complete() completed} will be
 * read from disk.
 *
 * <p>
 *
 * <pre>{@code
 * SnapshotStore snapshots = storage.openSnapshotStore("test");
 * Snapshot snapshot = snapshots.snapshot(1);
 *
 * }</pre>
 *
 * To create a new {@link Snapshot}, use the {@link #newSnapshot(long, long, WallClockTimestamp)}
 * method. Each snapshot must be created with a unique {@code index} which represents the index of
 * the server state machine at the point at which the snapshot was taken. Snapshot indices are used
 * to sort snapshots loaded from disk and apply them at the correct point in the state machine.
 *
 * <p>
 *
 * <pre>{@code
 * Snapshot snapshot = snapshots.create(10);
 * try (SnapshotWriter writer = snapshot.writer()) {
 *   ...
 * }
 * snapshot.complete();
 *
 * }</pre>
 *
 * Snapshots don't necessarily represent the beginning of the log. Typical Raft implementations take
 * a snapshot of the state machine state and then clear their logs up to that point. However, in
 * Raft a snapshot may actually only represent a subset of the state machine's state.
 */
public interface SnapshotStore extends AutoCloseable {

  /**
   * Returns the snapshot at the given index.
   *
   * @param index the index for which to lookup the snapshot
   * @return the snapshot at the given index or {@code null} if the snapshot doesn't exist
   */
  Snapshot getSnapshot(long index);

  @Override
  void close();

  /**
   * Returns the index of the current snapshot. Defaults to 0.
   *
   * @return the index of the current snapshot
   */
  long getCurrentSnapshotIndex();

  /**
   * Returns the current snapshot.
   *
   * @return the current snapshot
   */
  Snapshot getCurrentSnapshot();

  /**
   * Deletes a {@link SnapshotStore} from disk.
   *
   * <p>The snapshot store will be deleted by simply reading {@code snapshot} file names from disk
   * and deleting snapshot files directly. Deleting the snapshot store does not involve reading any
   * snapshot files into memory.
   */
  void delete();

  /**
   * Returns a new pending snapshot; this should be more or less like a snapshot, but is mainly used
   * for replication, and is not valid until committed.
   *
   * @param index the snapshot index
   * @param term the snapshot term
   * @param timestamp the snapshot timestamp
   * @return the new pending snapshot
   */
  PendingSnapshot newPendingSnapshot(long index, long term, WallClockTimestamp timestamp);

  /**
   * Creates a new snapshot.
   *
   * @param index The snapshot index.
   * @param timestamp The snapshot timestamp.
   * @return The snapshot.
   * @deprecated used by the old implementation
   */
  @Deprecated
  Snapshot newSnapshot(long index, long term, WallClockTimestamp timestamp);
}
