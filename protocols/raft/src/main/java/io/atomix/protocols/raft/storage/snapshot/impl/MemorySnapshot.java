/*
 * Copyright 2015-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.protocols.raft.storage.snapshot.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.storage.buffer.HeapBuffer;

/** In-memory snapshot backed by a {@link HeapBuffer}. */
final class MemorySnapshot extends DefaultSnapshot {

  private final HeapBuffer buffer;
  private final DefaultSnapshotDescriptor descriptor;

  MemorySnapshot(
      final HeapBuffer buffer,
      final DefaultSnapshotDescriptor descriptor,
      final DefaultSnapshotStore store) {
    super(descriptor, store);
    buffer.mark();
    this.buffer = checkNotNull(buffer, "buffer cannot be null");
    this.buffer.position(DefaultSnapshotDescriptor.BYTES).mark();
    this.descriptor = checkNotNull(descriptor, "descriptor cannot be null");
  }

  @Override
  public SnapshotWriter openWriter() {
    checkWriter();
    return new SnapshotWriter(buffer.reset().slice(), this);
  }

  @Override
  public synchronized SnapshotReader openReader() {
    return openReader(new SnapshotReader(buffer.reset().slice(), this), descriptor);
  }

  @Override
  public Snapshot complete() {
    buffer.flip().skip(DefaultSnapshotDescriptor.BYTES).mark();
    descriptor.lock();
    return super.complete();
  }

  @Override
  public void close() {
    buffer.close();
  }

  @Override
  public void closeWriter(final SnapshotWriter writer) {
    buffer.skip(writer.buffer().position()).mark();
    super.closeWriter(writer);
  }
}
