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
package io.atomix.protocols.raft.metrics;

import org.slf4j.LoggerFactory;

public class RaftMetrics {

  protected final String partition;

  public RaftMetrics(String partitionName) {
    int partitionId;
    try {
      final String[] parts = partitionName.split("-");
      partitionId = Integer.valueOf(parts[parts.length - 1]);
    } catch (Exception e) {
      LoggerFactory.getLogger(RaftMetrics.class)
          .debug("Cannot extract partition id from name {}, defaulting to 0", partitionName);
      partitionId = 0;
    }
    this.partition = String.valueOf(partitionId);
  }
}
