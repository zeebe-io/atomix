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

import io.prometheus.client.Histogram;

public class FollowerMetrics extends RaftMetrics {

  private static final Histogram APPEND_LATENCY =
      Histogram.build()
          .namespace("atomix")
          .name("follower_append_entries_time")
          .help("Time to append an request in a follower")
          .labelNames("partition")
          .register();

  private static final Histogram APPEND_LATENCY_PER_ENTRY =
      Histogram.build()
          .namespace("atomix")
          .name("follower_append_latency_per_entry_time")
          .help("Time to append an entry in a follower")
          .labelNames("partition")
          .register();

  public FollowerMetrics(String partitionName) {
    super(partitionName);
  }

  public double observeAppendLatency(Runnable append) {
    return APPEND_LATENCY.labels(partition).time(append);
  }

  public double observeAppendLatencyEntry(Runnable appendEntry) {
    return APPEND_LATENCY_PER_ENTRY.labels(partition).time(appendEntry);
  }
}
