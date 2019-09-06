/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.protocols.raft.zeebe;

import io.atomix.protocols.raft.storage.log.entry.TimestampedEntry;

/**
 * Stores a free-form entry.
 * <p>
 * Each entry is written with the leader's {@link #timestamp() timestamp} at the time the entry was logged
 * This gives state machines an approximation of time with which to react to the application of entries to the
 * state machine.
 */
public class ZeebeEntry extends TimestampedEntry {
  private final byte[] data;

  public ZeebeEntry(long term, long timestamp, byte[] data) {
    super(term, timestamp);
    this.data = data;
  }

  public byte[] getData() {
    return data;
  }
}
