/*
 * Copyright 2017-present Open Networking Foundation
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
 * limitations under the License.
 */
package io.atomix.raft.partition.impl;

import com.google.common.base.Preconditions;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.session.SessionId;
import io.atomix.raft.protocol.CloseSessionRequest;
import io.atomix.raft.protocol.CloseSessionResponse;
import io.atomix.raft.protocol.CommandRequest;
import io.atomix.raft.protocol.CommandResponse;
import io.atomix.raft.protocol.HeartbeatRequest;
import io.atomix.raft.protocol.HeartbeatResponse;
import io.atomix.raft.protocol.KeepAliveRequest;
import io.atomix.raft.protocol.KeepAliveResponse;
import io.atomix.raft.protocol.MetadataRequest;
import io.atomix.raft.protocol.MetadataResponse;
import io.atomix.raft.protocol.OpenSessionRequest;
import io.atomix.raft.protocol.OpenSessionResponse;
import io.atomix.raft.protocol.PublishRequest;
import io.atomix.raft.protocol.QueryRequest;
import io.atomix.raft.protocol.QueryResponse;
import io.atomix.raft.protocol.RaftClientProtocol;
import io.atomix.raft.protocol.ResetRequest;
import io.atomix.utils.serializer.Serializer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Raft client protocol that uses a cluster communicator. */
public class RaftClientCommunicator implements RaftClientProtocol {

  private final RaftMessageContext context;
  private final Serializer serializer;
  private final ClusterCommunicationService clusterCommunicator;

  public RaftClientCommunicator(
      Serializer serializer, ClusterCommunicationService clusterCommunicator) {
    this(null, serializer, clusterCommunicator);
  }

  public RaftClientCommunicator(
      String prefix, Serializer serializer, ClusterCommunicationService clusterCommunicator) {
    this.context = new RaftMessageContext(prefix);
    this.serializer = Preconditions.checkNotNull(serializer, "serializer cannot be null");
    this.clusterCommunicator =
        Preconditions.checkNotNull(clusterCommunicator, "clusterCommunicator cannot be null");
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(
      MemberId memberId, OpenSessionRequest request) {
    return sendAndReceive(context.openSessionSubject, request, memberId);
  }

  private <T, U> CompletableFuture<U> sendAndReceive(String subject, T request, MemberId memberId) {
    return clusterCommunicator.send(
        subject, request, serializer::encode, serializer::decode, memberId);
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(
      MemberId memberId, CloseSessionRequest request) {
    return sendAndReceive(context.closeSessionSubject, request, memberId);
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(
      MemberId memberId, KeepAliveRequest request) {
    return sendAndReceive(context.keepAliveSubject, request, memberId);
  }

  @Override
  public CompletableFuture<QueryResponse> query(MemberId memberId, QueryRequest request) {
    return sendAndReceive(context.querySubject, request, memberId);
  }

  @Override
  public CompletableFuture<CommandResponse> command(MemberId memberId, CommandRequest request) {
    return sendAndReceive(context.commandSubject, request, memberId);
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(MemberId memberId, MetadataRequest request) {
    return sendAndReceive(context.metadataSubject, request, memberId);
  }

  @Override
  public void reset(Set<MemberId> members, ResetRequest request) {
    clusterCommunicator.multicast(
        context.resetSubject(request.session()), request, serializer::encode, members);
  }

  @Override
  public void registerHeartbeatHandler(
      Function<HeartbeatRequest, CompletableFuture<HeartbeatResponse>> handler) {
    clusterCommunicator.subscribe(
        context.heartbeatSubject, serializer::decode, handler, serializer::encode);
  }

  @Override
  public void unregisterHeartbeatHandler() {
    clusterCommunicator.unsubscribe(context.heartbeatSubject);
  }

  @Override
  public void registerPublishListener(
      SessionId sessionId, Consumer<PublishRequest> listener, Executor executor) {
    clusterCommunicator.subscribe(
        context.publishSubject(sessionId.id()), serializer::decode, listener, executor);
  }

  @Override
  public void unregisterPublishListener(SessionId sessionId) {
    clusterCommunicator.unsubscribe(context.publishSubject(sessionId.id()));
  }
}