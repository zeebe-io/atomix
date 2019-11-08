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

import io.atomix.storage.journal.Indexed;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ZeebeLogAppender {

  /**
   * Appends an entry to the local Raft log and schedules replication to each follower.
   *
   * @param lowestPosition lowest record position in the data buffer
   * @param highestPosition highest record position in the data buffer
   * @param data data to store in the entry
   * @return a future which is completed with the indexed entry without waiting for replication
   */
  CompletableFuture<Indexed<ZeebeEntry>> appendEntry(
      long lowestPosition, long highestPosition, ByteBuffer data);
}
