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
package io.atomix.protocols.raft.zeebe.partition;

import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.protocols.raft.partition.RaftPartitionGroupConfig;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;

public class ZeebeRaftPartitionGroup extends RaftPartitionGroup {

  public static final ZeebeRaftPartitionGroup.Type TYPE = new ZeebeRaftPartitionGroup.Type();

  public ZeebeRaftPartitionGroup(RaftPartitionGroupConfig config) {
    super(config);
  }

  /**
   * Returns a new Raft partition group builder.
   *
   * @param name the partition group name
   * @return a new partition group builder
   */
  public static Builder builder(String name) {
    return new Builder(new ZeebeRaftPartitionGroupConfig().setName(name));
  }

  /** Raft partition group type. */
  public static class Type implements PartitionGroup.Type<RaftPartitionGroupConfig> {

    private static final String NAME = "zeebe-raft";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public Namespace namespace() {
      return Namespace.builder()
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID + 100)
          .register(RaftPartitionGroup.TYPE.namespace())
          .build();
    }

    @Override
    public ManagedPartitionGroup newPartitionGroup(RaftPartitionGroupConfig config) {
      return new ZeebeRaftPartitionGroup(config);
    }

    @Override
    public RaftPartitionGroupConfig newConfig() {
      return new ZeebeRaftPartitionGroupConfig();
    }
  }

  public static class Builder extends RaftPartitionGroup.Builder {

    protected Builder(RaftPartitionGroupConfig config) {
      super(config);
    }
  }
}
