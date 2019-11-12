package io.atomix.protocols.raft.storage.snapshot.impl;

import io.atomix.utils.Identifier;

public class DefaultSnapshotChunkId implements Identifier<Long> {
  private final long offset;

  public DefaultSnapshotChunkId(final long offset) {
    this.offset = offset;
  }

  @Override
  public Long id() {
    return offset;
  }

  public long getOffset() {
    return offset;
  }
}
