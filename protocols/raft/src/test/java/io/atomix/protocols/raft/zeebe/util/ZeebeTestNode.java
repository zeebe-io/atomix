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
package io.atomix.protocols.raft.zeebe.util;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.impl.ClasspathScanningPrimitiveTypeRegistry;
import io.atomix.primitive.impl.DefaultPrimitiveTypeRegistry;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionGroupTypeRegistry;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.protocols.raft.partition.impl.RaftPartitionServer;
import io.atomix.storage.StorageLevel;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ZeebeTestNode {

  public static final String CLUSTER_ID = "zeebe";
  private static final String DATA_PARTITION_GROUP_NAME = "data";
  private static final String HOST = "localhost";
  private static final int BASE_PORT = 10_000;
  private final Member member;
  private final Node node;
  private final File directory;

  private RaftPartitionGroup dataPartitionGroup;
  private ManagedPartitionService partitionService;
  private AtomixCluster cluster;

  public ZeebeTestNode(final int id, final File directory) {
    final String textualId = String.valueOf(id);

    this.directory = directory;
    this.node = Node.builder().withId(textualId).withHost(HOST).withPort(BASE_PORT + id).build();
    this.member = Member.member(MemberId.from(textualId), node.address());
  }

  public RaftPartitionServer getPartitionServer(final int id) {
    return getPartition(id).getServer();
  }

  RaftPartition getPartition(final int id) {
    return (RaftPartition) getDataPartitionGroup().getPartition(String.valueOf(id));
  }

  private RaftPartitionGroup getDataPartitionGroup() {
    return (RaftPartitionGroup) partitionService.getPartitionGroup(DATA_PARTITION_GROUP_NAME);
  }

  public CompletableFuture<Void> start(final Collection<ZeebeTestNode> nodes) {
    cluster = buildCluster(nodes);
    dataPartitionGroup =
        buildPartitionGroup(RaftPartitionGroup.builder(DATA_PARTITION_GROUP_NAME), nodes)
            .withStateMachineFactory(ZeebeRaftStateMachine::new)
            .build();
    partitionService =
        buildPartitionService(cluster.getMembershipService(), cluster.getCommunicationService());

    return cluster.start().thenCompose(ignored -> partitionService.start()).thenApply(v -> null);
  }

  private AtomixCluster buildCluster(final Collection<ZeebeTestNode> nodes) {
    return AtomixCluster.builder()
        .withAddress(node.address())
        .withClusterId(CLUSTER_ID)
        .withMembershipProvider(buildDiscoveryProvider(nodes))
        .withMemberId(getMemberId())
        .build();
  }

  public MemberId getMemberId() {
    return member.id();
  }

  private NodeDiscoveryProvider buildDiscoveryProvider(final Collection<ZeebeTestNode> nodes) {
    return BootstrapDiscoveryProvider.builder()
        .withNodes(nodes.stream().map(ZeebeTestNode::getNode).collect(Collectors.toList()))
        .build();
  }

  public Node getNode() {
    return node;
  }

  private RaftPartitionGroup.Builder buildPartitionGroup(
      final RaftPartitionGroup.Builder builder, final Collection<ZeebeTestNode> nodes) {
    final Set<Member> members =
        nodes.stream().map(ZeebeTestNode::getMember).collect(Collectors.toSet());
    members.add(member);

    return builder
        .withDataDirectory(directory)
        .withMembers(members.toArray(new Member[0]))
        .withNumPartitions(1)
        .withPartitionSize(members.size())
        .withFlushOnCommit()
        .withStorageLevel(StorageLevel.DISK)
        .withSegmentSize(1024L)
        .withMaxEntrySize(512);
  }

  public Member getMember() {
    return member;
  }

  private ManagedPartitionService buildPartitionService(
      final ClusterMembershipService clusterMembershipService,
      final ClusterCommunicationService messagingService) {
    final ClasspathScanningPrimitiveTypeRegistry registry =
        new ClasspathScanningPrimitiveTypeRegistry(this.getClass().getClassLoader());
    final List<ManagedPartitionGroup> partitionGroups =
        Collections.singletonList(dataPartitionGroup);

    return new DefaultPartitionService(
        clusterMembershipService,
        messagingService,
        new DefaultPrimitiveTypeRegistry(registry.getPrimitiveTypes()),
        partitionGroups,
        new DefaultPartitionGroupTypeRegistry(Collections.singleton(RaftPartitionGroup.TYPE)));
  }

  public CompletableFuture<Void> stop() {
    return partitionService
        .stop()
        .thenCompose(ignored -> dataPartitionGroup.close())
        .thenCompose(ignored -> cluster.stop());
  }

  public AtomixCluster getCluster() {
    return cluster;
  }

  @Override
  public String toString() {
    return "ZeebeTestNode{" + "member=" + member + '}';
  }
}
