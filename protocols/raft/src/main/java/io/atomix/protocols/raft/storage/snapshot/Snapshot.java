package io.atomix.protocols.raft.storage.snapshot;

import io.atomix.utils.time.WallClockTimestamp;

/**
 * Manages reading and writing a single snapshot.
 *
 * <p>Snapshots are read and written by a {@link SnapshotReader} and {@link SnapshotWriter}
 * respectively. To create a reader or writer, use the {@link #openReader()} and {@link
 * #openWriter()} methods.
 *
 * <p>
 *
 * <pre>{@code
 * Snapshot snapshot = snapshotStore.snapshot(1);
 * try (SnapshotWriter writer = snapshot.writer()) {
 *   writer.writeString("Hello world!");
 * }
 * snapshot.complete();
 *
 * }</pre>
 *
 * A {@link SnapshotReader} is not allowed to be created until a {@link SnapshotWriter} has
 * completed writing the snapshot file and the snapshot has been marked {@link #complete()
 * complete}. This allows snapshots to effectively be written and closed but not completed until
 * other conditions are met. Prior to the completion of a snapshot, a failure and recovery of the
 * parent {@link SnapshotStore} will <em>not</em> recover an incomplete snapshot. Once a snapshot is
 * complete, the snapshot becomes immutable, can be recovered after a failure, and can be read by
 * multiple readers concurrently.
 */
public interface Snapshot extends AutoCloseable {

  /**
   * Returns the snapshot timestamp.
   *
   * <p>The timestamp is the wall clock time at the {@link #index()} at which the snapshot was
   * taken.
   *
   * @return The snapshot timestamp.
   */
  WallClockTimestamp timestamp();

  /**
   * Returns the snapshot format version.
   *
   * @return the snapshot format version
   */
  int version();

  /**
   * Opens a new snapshot writer.
   *
   * <p>Only a single {@link SnapshotWriter} per {@link Snapshot} can be created. The single writer
   * must write the snapshot in full and {@link #complete()} the snapshot to persist it to disk and
   * make it available for {@link #openReader() reads}.
   *
   * @return A new snapshot writer.
   * @throws IllegalStateException if a writer was already created or the snapshot is {@link
   *     #complete() complete}
   */
  SnapshotWriter openWriter();

  /**
   * Opens a new snapshot reader.
   *
   * <p>A {@link SnapshotReader} can only be created for a snapshot that has been fully written and
   * {@link #complete() completed}. Multiple concurrent readers can be created for the same snapshot
   * since completed snapshots are immutable.
   *
   * @return A new snapshot reader.
   * @throws IllegalStateException if the snapshot is not {@link #complete() complete}
   */
  SnapshotReader openReader();

  /**
   * Completes writing the snapshot to persist it and make it available for reads.
   *
   * <p>Snapshot writers must call this method to persist a snapshot to disk. Prior to completing a
   * snapshot, failure and recovery of the parent {@link SnapshotStore} will not result in recovery
   * of this snapshot. Additionally, no {@link #openReader() readers} can be created until the
   * snapshot has been completed.
   *
   * @return The completed snapshot.
   */
  Snapshot complete();

  /** Closes the snapshot. */
  @Override
  void close();

  /** Deletes the snapshot. */
  void delete();

  /**
   * Returns the snapshot index.
   *
   * <p>The snapshot index is the index of the state machine at the point at which the snapshot was
   * written.
   *
   * @return The snapshot index.
   */
  long index();

  /**
   * Returns the snapshot term.
   *
   * <p>The snapshot term is the term of the state machine at the point at which the snapshot was
   * written.
   *
   * @return The snapshot term.
   */
  long term();

  /** Closes the current snapshot reader. */
  void closeReader(SnapshotReader reader);

  /** Closes the current snapshot writer. */
  void closeWriter(SnapshotWriter writer);
}
