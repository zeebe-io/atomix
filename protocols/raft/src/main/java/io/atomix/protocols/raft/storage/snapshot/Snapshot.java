package io.atomix.protocols.raft.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.impl.SnapshotReader;
import io.atomix.protocols.raft.storage.snapshot.impl.SnapshotWriter;
import io.atomix.utils.time.WallClockTimestamp;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Manages reading and writing a single snapshot.
 *
 * <p>Snapshots are read using {@link SnapshotChunkReader}. To create a reader, use the {@link
 * #newChunkReader()}.
 *
 * <p>Snapshots are written using a {@link PendingSnapshot} implementation, which handles creating
 * temporary snapshots until they are committed.
 *
 * <pre>{@code
 * Snapshot snapshot = snapshotStore.snapshot(1);
 * try (SnapshotChunkReader reader = snapshot.newChunkReader()) {
 *   reader.seek(previousChunkId);
 *   if (reader.hasNext()) {
 *     final SnapshotChunk chunk = reader.next();
 *     // do something
 *   }
 * }
 * }</pre>
 */
public interface Snapshot extends AutoCloseable, Comparable<Snapshot> {

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

  /**
   * Returns a new snapshot chunk reader for this snapshot. Chunk readers are meant to be one-time
   * use and as such don't have to be thread-safe.
   *
   * @return a new snapshot chunk reader
   */
  SnapshotChunkReader newChunkReader();

  /** Closes the snapshot. */
  @Override
  void close();

  /** Deletes the snapshot. */
  void delete();

  /** @return a path to the snapshot location */
  Path getPath();

  @Override
  default int compareTo(Snapshot other) {
    return Comparator.comparingLong(Snapshot::index)
        .thenComparingLong(Snapshot::term)
        .thenComparing(Snapshot::timestamp)
        .compare(this, other);
  }

  /**
   * Completes writing the snapshot to persist it and make it available for reads.
   *
   * @return The completed snapshot.
   * @deprecated used by the old implementation, superseded {@link PendingSnapshot#commit()}
   */
  @Deprecated
  Snapshot complete();

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
   * @deprecated used by the old implementation, use the new chunk writers
   */
  @Deprecated
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
   * @deprecated used by the old implementation, use the new chunk readers
   */
  @Deprecated
  SnapshotReader openReader();

  /** Closes the current snapshot reader. */
  @Deprecated
  void closeReader(SnapshotReader reader);

  /** Closes the current snapshot writer. */
  @Deprecated
  void closeWriter(SnapshotWriter writer);
}
