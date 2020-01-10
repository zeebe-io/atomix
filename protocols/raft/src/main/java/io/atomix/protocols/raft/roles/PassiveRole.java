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
 * limitations under the License.
 */
package io.atomix.protocols.raft.roles;

import io.atomix.primitive.PrimitiveException;
import io.atomix.protocols.raft.RaftError;
import io.atomix.protocols.raft.RaftException;
import io.atomix.protocols.raft.RaftServer;
import io.atomix.protocols.raft.ReadConsistency;
import io.atomix.protocols.raft.impl.OperationResult;
import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.protocols.raft.metrics.SnapshotReplicationMetrics;
import io.atomix.protocols.raft.protocol.AppendRequest;
import io.atomix.protocols.raft.protocol.AppendResponse;
import io.atomix.protocols.raft.protocol.CloseSessionRequest;
import io.atomix.protocols.raft.protocol.CloseSessionResponse;
import io.atomix.protocols.raft.protocol.CommandRequest;
import io.atomix.protocols.raft.protocol.CommandResponse;
import io.atomix.protocols.raft.protocol.InstallRequest;
import io.atomix.protocols.raft.protocol.InstallResponse;
import io.atomix.protocols.raft.protocol.JoinRequest;
import io.atomix.protocols.raft.protocol.JoinResponse;
import io.atomix.protocols.raft.protocol.KeepAliveRequest;
import io.atomix.protocols.raft.protocol.KeepAliveResponse;
import io.atomix.protocols.raft.protocol.LeaveRequest;
import io.atomix.protocols.raft.protocol.LeaveResponse;
import io.atomix.protocols.raft.protocol.MetadataRequest;
import io.atomix.protocols.raft.protocol.MetadataResponse;
import io.atomix.protocols.raft.protocol.OpenSessionRequest;
import io.atomix.protocols.raft.protocol.OpenSessionResponse;
import io.atomix.protocols.raft.protocol.OperationResponse;
import io.atomix.protocols.raft.protocol.PollRequest;
import io.atomix.protocols.raft.protocol.PollResponse;
import io.atomix.protocols.raft.protocol.QueryRequest;
import io.atomix.protocols.raft.protocol.QueryResponse;
import io.atomix.protocols.raft.protocol.RaftResponse;
import io.atomix.protocols.raft.protocol.ReconfigureRequest;
import io.atomix.protocols.raft.protocol.ReconfigureResponse;
import io.atomix.protocols.raft.protocol.VoteRequest;
import io.atomix.protocols.raft.protocol.VoteResponse;
import io.atomix.protocols.raft.session.RaftSession;
import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.storage.log.RaftLogWriter;
import io.atomix.protocols.raft.storage.log.entry.QueryEntry;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.protocols.raft.storage.snapshot.PendingSnapshot;
import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.storage.StorageException;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.time.WallClockTimestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Passive state. */
public class PassiveRole extends InactiveRole {
  private final SnapshotReplicationMetrics snapshotReplicationMetrics;

  private long pendingSnapshotStartTimestamp;
  private PendingSnapshot pendingSnapshot;

  public PassiveRole(RaftContext context) {
    super(context);

    this.snapshotReplicationMetrics = new SnapshotReplicationMetrics(context.getName());
    this.snapshotReplicationMetrics.setCount(0);
  }

  @Override
  public CompletableFuture<RaftRole> start() {
    return super.start().thenRun(this::truncateUncommittedEntries).thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (pendingSnapshot != null) {
      abortPendingSnapshot();
    }

    return super.stop();
  }

  /** Truncates uncommitted entries from the log. */
  private void truncateUncommittedEntries() {
    if (role() == RaftServer.Role.PASSIVE) {
      final RaftLogWriter writer = raft.getLogWriter();
      writer.truncate(raft.getCommitIndex());
    }
  }

  @Override
  public RaftServer.Role role() {
    return RaftServer.Role.PASSIVE;
  }

  @Override
  public CompletableFuture<MetadataResponse> onMetadata(MetadataRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              MetadataResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::metadata)
          .exceptionally(
              error ->
                  MetadataResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<OpenSessionResponse> onOpenSession(OpenSessionRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              OpenSessionResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::openSession)
          .exceptionally(
              error ->
                  OpenSessionResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<KeepAliveResponse> onKeepAlive(KeepAliveRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              KeepAliveResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::keepAlive)
          .exceptionally(
              error ->
                  KeepAliveResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<CloseSessionResponse> onCloseSession(CloseSessionRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              CloseSessionResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::closeSession)
          .exceptionally(
              error ->
                  CloseSessionResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<InstallResponse> onInstall(InstallRequest request) {
    raft.checkThread();
    logRequest(request);
    updateTermAndLeader(request.currentTerm(), request.leader());

    log.debug("Received snapshot {} chunk from {}", request.index(), request.leader());

    // If the request is for a lesser term, reject the request.
    if (request.currentTerm() < raft.getTerm()) {
      return CompletableFuture.completedFuture(
          logResponse(
              InstallResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(
                      RaftError.Type.ILLEGAL_MEMBER_STATE,
                      "Request term is less than the local term " + request.currentTerm())
                  .build()));
    }

    // If the index has already been applied, we have enough state to populate the state machine up
    // to this index.
    // Skip the snapshot and response successfully.
    if (raft.getLastApplied() > request.index()) {
      return CompletableFuture.completedFuture(
          logResponse(InstallResponse.builder().withStatus(RaftResponse.Status.OK).build()));
    }

    // If a snapshot is currently being received and the snapshot versions don't match, simply
    // close the existing snapshot. This is a naive implementation that assumes that the leader
    // will be responsible in sending the correct snapshot to this server. Leaders must dictate
    // where snapshots must be sent since entries can still legitimately exist prior to the
    // snapshot,
    // and so snapshots aren't simply sent at the beginning of the follower's log, but rather the
    // leader dictates when a snapshot needs to be sent.
    if (pendingSnapshot != null && request.index() != pendingSnapshot.index()) {
      abortPendingSnapshot();
    }

    // If the snapshot already exists locally, do not overwrite it with a replicated snapshot.
    // Simply reply to the
    // request successfully.
    final Snapshot existingSnapshot = raft.getSnapshotStore().getSnapshot(request.index());
    if (existingSnapshot != null) {
      if (pendingSnapshot != null) {
        abortPendingSnapshot();
      }

      return CompletableFuture.completedFuture(
          logResponse(InstallResponse.builder().withStatus(RaftResponse.Status.OK).build()));
    }

    // If there is no pending snapshot, create a new snapshot.
    if (pendingSnapshot == null) {
      // if we have no pending snapshot then the request must be the first chunk, otherwise we could
      // receive an old request and end up in a strange state
      if (!request.isInitial()) {
        return CompletableFuture.completedFuture(
            logResponse(
                InstallResponse.builder()
                    .withStatus(RaftResponse.Status.ERROR)
                    .withError(
                        RaftError.Type.ILLEGAL_MEMBER_STATE, "Request chunk offset is invalid")
                    .build()));
      }

      pendingSnapshot =
          raft.getSnapshotStore()
              .newPendingSnapshot(
                  request.index(), request.term(), WallClockTimestamp.from(request.timestamp()));
      pendingSnapshotStartTimestamp = System.currentTimeMillis();
      snapshotReplicationMetrics.incrementCount();
    } else {
      // skip if we already have this chunk
      if (pendingSnapshot.containsChunk(request.chunkId())) {
        return CompletableFuture.completedFuture(
            logResponse(InstallResponse.builder().withStatus(RaftResponse.Status.OK).build()));
      }

      // fail the request if this is not the expected next chunk
      if (!pendingSnapshot.isExpectedChunk(request.chunkId())) {
        return CompletableFuture.completedFuture(
            logResponse(
                InstallResponse.builder()
                    .withStatus(RaftResponse.Status.ERROR)
                    .withError(
                        RaftError.Type.ILLEGAL_MEMBER_STATE,
                        "Request chunk is was received out of order")
                    .build()));
      }
    }

    // Write the data to the snapshot.
    pendingSnapshot.write(request.chunkId(), request.data());

    // If the snapshot is complete, store the snapshot and reset state, otherwise update the next
    // snapshot offset.
    if (request.complete()) {
      final long index = pendingSnapshot.index();
      log.debug("Committing snapshot {}", pendingSnapshot);
      try {
        final long elapsed = System.currentTimeMillis() - pendingSnapshotStartTimestamp;
        pendingSnapshot.commit();
        pendingSnapshot = null;
        pendingSnapshotStartTimestamp = 0L;

        snapshotReplicationMetrics.decrementCount();
        snapshotReplicationMetrics.observeDuration(elapsed);
      } catch (Exception e) {
        log.error("Failed to commit pending snapshot {}, rolling back", pendingSnapshot, e);
        abortPendingSnapshot();
        return CompletableFuture.completedFuture(
            logResponse(
                InstallResponse.builder()
                    .withStatus(RaftResponse.Status.ERROR)
                    .withError(
                        RaftError.Type.APPLICATION_ERROR, "Failed to commit pending snapshot")
                    .build()));
      }

      // Throw away existing log if it is not up-to-date with the snapshot index.
      if (raft.getLogWriter().getLastIndex() < index) {
        raft.getLogWriter().reset(index + 1);
      }
    } else {
      // should probably handle the case where the leader did not mark the snapshot as complete but
      // did not send a next chunk ID either
      pendingSnapshot.setNextExpected(request.nextChunkId());
    }

    return CompletableFuture.completedFuture(
        logResponse(InstallResponse.builder().withStatus(RaftResponse.Status.OK).build()));
  }

  @Override
  public CompletableFuture<JoinResponse> onJoin(JoinRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              JoinResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::join)
          .exceptionally(
              error ->
                  JoinResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<ReconfigureResponse> onReconfigure(ReconfigureRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              ReconfigureResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::reconfigure)
          .exceptionally(
              error ->
                  ReconfigureResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<LeaveResponse> onLeave(LeaveRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              LeaveResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::leave)
          .exceptionally(
              error ->
                  LeaveResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(AppendRequest request) {
    raft.checkThread();
    logRequest(request);
    updateTermAndLeader(request.term(), request.leader());
    return handleAppend(request);
  }

  @Override
  public CompletableFuture<PollResponse> onPoll(PollRequest request) {
    raft.checkThread();
    logRequest(request);

    return CompletableFuture.completedFuture(
        logResponse(
            PollResponse.builder()
                .withStatus(RaftResponse.Status.ERROR)
                .withError(RaftError.Type.ILLEGAL_MEMBER_STATE, "Cannot poll RESERVE member")
                .build()));
  }

  @Override
  public CompletableFuture<VoteResponse> onVote(VoteRequest request) {
    raft.checkThread();
    logRequest(request);
    updateTermAndLeader(request.term(), null);

    return CompletableFuture.completedFuture(
        logResponse(
            VoteResponse.builder()
                .withStatus(RaftResponse.Status.ERROR)
                .withError(
                    RaftError.Type.ILLEGAL_MEMBER_STATE, "Cannot request vote from RESERVE member")
                .build()));
  }

  @Override
  public CompletableFuture<CommandResponse> onCommand(CommandRequest request) {
    raft.checkThread();
    logRequest(request);

    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              CommandResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    } else {
      return forward(request, raft.getProtocol()::command)
          .exceptionally(
              error ->
                  CommandResponse.builder()
                      .withStatus(RaftResponse.Status.ERROR)
                      .withError(RaftError.Type.NO_LEADER)
                      .build())
          .thenApply(this::logResponse);
    }
  }

  @Override
  public CompletableFuture<QueryResponse> onQuery(QueryRequest request) {
    raft.checkThread();
    logRequest(request);

    // If this server has not yet applied entries up to the client's session ID, forward the
    // query to the leader. This ensures that a follower does not tell the client its session
    // doesn't exist if the follower hasn't had a chance to see the session's registration entry.
    if (raft.getState() != RaftContext.State.READY || raft.getLastApplied() < request.session()) {
      log.trace("State out of sync, forwarding query to leader");
      return queryForward(request);
    }

    // Look up the client's session.
    final RaftSession session = raft.getSessions().getSession(request.session());
    if (session == null) {
      log.trace("State out of sync, forwarding query to leader");
      return queryForward(request);
    }

    // If the session's consistency level is SEQUENTIAL, handle the request here, otherwise forward
    // it.
    if (session.readConsistency() == ReadConsistency.SEQUENTIAL) {

      // If the commit index is not in the log then we've fallen too far behind the leader to
      // perform a local query.
      // Forward the request to the leader.
      if (raft.getLogWriter().getLastIndex() < raft.getCommitIndex()) {
        log.trace("State out of sync, forwarding query to leader");
        return queryForward(request);
      }

      final Indexed<QueryEntry> entry =
          new Indexed<>(
              request.index(),
              new QueryEntry(
                  raft.getTerm(),
                  System.currentTimeMillis(),
                  request.session(),
                  request.sequenceNumber(),
                  request.operation()),
              0);

      return applyQuery(entry).thenApply(this::logResponse);
    } else {
      return queryForward(request);
    }
  }

  private void abortPendingSnapshot() {
    log.debug("Rolling back snapshot {}", pendingSnapshot);
    pendingSnapshot.abort();
    pendingSnapshot = null;
    pendingSnapshotStartTimestamp = 0L;

    // should we also observe the current size when it was aborted? not sure, might skew results
    snapshotReplicationMetrics.decrementCount();
  }

  /** Forwards the query to the leader. */
  private CompletableFuture<QueryResponse> queryForward(QueryRequest request) {
    if (raft.getLeader() == null) {
      return CompletableFuture.completedFuture(
          logResponse(
              QueryResponse.builder()
                  .withStatus(RaftResponse.Status.ERROR)
                  .withError(RaftError.Type.NO_LEADER)
                  .build()));
    }

    log.trace("Forwarding {}", request);
    return forward(request, raft.getProtocol()::query)
        .exceptionally(
            error ->
                QueryResponse.builder()
                    .withStatus(RaftResponse.Status.ERROR)
                    .withError(RaftError.Type.NO_LEADER)
                    .build())
        .thenApply(this::logResponse);
  }

  /** Applies a query to the state machine. */
  protected CompletableFuture<QueryResponse> applyQuery(Indexed<QueryEntry> entry) {
    // In the case of the leader, the state machine is always up to date, so no queries will be
    // queued and all query
    // indexes will be the last applied index.
    final CompletableFuture<QueryResponse> future = new CompletableFuture<>();
    raft.getServiceManager()
        .<OperationResult>apply(entry)
        .whenComplete(
            (result, error) -> {
              completeOperation(result, QueryResponse.builder(), error, future);
            });
    return future;
  }

  /** Completes an operation. */
  protected <T extends OperationResponse> void completeOperation(
      OperationResult result,
      OperationResponse.Builder<?, T> builder,
      Throwable error,
      CompletableFuture<T> future) {
    if (result != null) {
      builder.withIndex(result.index());
      builder.withEventIndex(result.eventIndex());
      if (result.failed()) {
        error = result.error();
      }
    }

    if (error == null) {
      if (result == null) {
        future.complete(
            builder
                .withStatus(RaftResponse.Status.ERROR)
                .withError(RaftError.Type.PROTOCOL_ERROR)
                .build());
      } else {
        future.complete(
            builder.withStatus(RaftResponse.Status.OK).withResult(result.result()).build());
      }
    } else if (error instanceof CompletionException && error.getCause() instanceof RaftException) {
      future.complete(
          builder
              .withStatus(RaftResponse.Status.ERROR)
              .withError(((RaftException) error.getCause()).getType(), error.getMessage())
              .build());
    } else if (error instanceof RaftException) {
      future.complete(
          builder
              .withStatus(RaftResponse.Status.ERROR)
              .withError(((RaftException) error).getType(), error.getMessage())
              .build());
    } else if (error instanceof PrimitiveException.ServiceException) {
      log.warn("An application error occurred: {}", error.getCause());
      future.complete(
          builder
              .withStatus(RaftResponse.Status.ERROR)
              .withError(RaftError.Type.APPLICATION_ERROR)
              .build());
    } else {
      log.warn("An unexpected error occurred: {}", error);
      future.complete(
          builder
              .withStatus(RaftResponse.Status.ERROR)
              .withError(RaftError.Type.PROTOCOL_ERROR, error.getMessage())
              .build());
    }
  }

  /** Handles an AppendRequest. */
  protected CompletableFuture<AppendResponse> handleAppend(AppendRequest request) {
    final CompletableFuture<AppendResponse> future = new CompletableFuture<>();

    // Check that the term of the given request matches the local term or update the term.
    if (!checkTerm(request, future)) {
      return future;
    }

    // Check that the previous index/term matches the local log's last entry.
    if (!checkPreviousEntry(request, future)) {
      return future;
    }

    // Append the entries to the log.
    appendEntries(request, future);
    return future;
  }

  /**
   * Checks the leader's term of the given AppendRequest, returning a boolean indicating whether to
   * continue handling the request.
   */
  protected boolean checkTerm(AppendRequest request, CompletableFuture<AppendResponse> future) {
    final RaftLogWriter writer = raft.getLogWriter();
    if (request.term() < raft.getTerm()) {
      log.debug(
          "Rejected {}: request term is less than the current term ({})", request, raft.getTerm());
      return failAppend(writer.getLastIndex(), future);
    }
    return true;
  }

  /**
   * Checks the previous index of the given AppendRequest, returning a boolean indicating whether to
   * continue handling the request.
   */
  protected boolean checkPreviousEntry(
      AppendRequest request, CompletableFuture<AppendResponse> future) {
    final RaftLogWriter writer = raft.getLogWriter();

    // If the previous term is set, validate that it matches the local log.
    // We check the previous log term since that indicates whether any entry is present in the
    // leader's
    // log at the previous log index. prevLogTerm is 0 only when it is the first entry of the log.
    if (request.prevLogTerm() != 0) {
      // Get the last entry written to the log.
      final Indexed<RaftLogEntry> lastEntry = writer.getLastEntry();

      // If the local log is non-empty...
      if (lastEntry != null) {
        return checkPreviousEntry(request, lastEntry.index(), lastEntry.entry().term(), future);
      } else {
        final Snapshot currentSnapshot = raft.getSnapshotStore().getCurrentSnapshot();
        if (currentSnapshot != null) {
          return checkPreviousEntry(
              request, currentSnapshot.index(), currentSnapshot.term(), future);
        } else {
          // If the previous log index is set and the last entry is null and there is no snapshot,
          // fail the append.
          if (request.prevLogIndex() > 0) {
            log.debug(
                "Rejected {}: Previous index ({}) is greater than the local log's last index (0)",
                request,
                request.prevLogIndex());
            return failAppend(0, future);
          }
        }
      }
    }
    return true;
  }

  private boolean checkPreviousEntry(
      AppendRequest request,
      long lastEntryIndex,
      long lastEntryTerm,
      CompletableFuture<AppendResponse> future) {

    final RaftLogReader reader = raft.getLogReader();

    // If the previous log index is greater than the last entry index, fail the attempt.
    if (request.prevLogIndex() > lastEntryIndex) {
      log.debug(
          "Rejected {}: Previous index ({}) is greater than the local log's last index ({})",
          request,
          request.prevLogIndex(),
          lastEntryIndex);
      return failAppend(lastEntryIndex, future);
    }

    // If the previous log index is less than the last written entry index, look up the entry.
    if (request.prevLogIndex() < lastEntryIndex) {
      // Reset the reader to the previous log index.
      if (reader.getNextIndex() != request.prevLogIndex()) {
        reader.reset(request.prevLogIndex());
      }

      // The previous entry should exist in the log if we've gotten this far.
      if (!reader.hasNext()) {
        log.debug("Rejected {}: Previous entry does not exist in the local log", request);
        return failAppend(lastEntryIndex, future);
      }

      // Read the previous entry and validate that the term matches the request previous log term.
      final Indexed<RaftLogEntry> previousEntry = reader.next();
      if (request.prevLogTerm() != previousEntry.entry().term()) {
        log.debug(
            "Rejected {}: Previous entry term ({}) does not match local log's term for the same entry ({})",
            request,
            request.prevLogTerm(),
            previousEntry.entry().term());
        return failAppend(request.prevLogIndex() - 1, future);
      }
    }
    // If the previous log term doesn't equal the last entry term, fail the append, sending the
    // prior entry.
    else if (request.prevLogTerm() != lastEntryTerm) {
      log.debug(
          "Rejected {}: Previous entry term ({}) does not equal the local log's last term ({})",
          request,
          request.prevLogTerm(),
          lastEntryTerm);
      return failAppend(request.prevLogIndex() - 1, future);
    }
    return true;
  }

  /** Appends entries from the given AppendRequest. */
  protected void appendEntries(AppendRequest request, CompletableFuture<AppendResponse> future) {
    // Compute the last entry index from the previous log index and request entry count.
    final long lastEntryIndex = request.prevLogIndex() + request.entries().size();

    // Ensure the commitIndex is not increased beyond the index of the last entry in the request.
    final long commitIndex =
        Math.max(raft.getCommitIndex(), Math.min(request.commitIndex(), lastEntryIndex));

    // Track the last log index while entries are appended.
    long lastLogIndex = request.prevLogIndex();

    if (!request.entries().isEmpty()) {
      final RaftLogWriter writer = raft.getLogWriter();
      final RaftLogReader reader = raft.getLogReader();

      // If the previous term is zero, that indicates the previous index represents the beginning of
      // the log.
      // Reset the log to the previous index plus one.
      if (request.prevLogTerm() == 0) {
        log.debug("Reset first index to {}", request.prevLogIndex() + 1);
        writer.reset(request.prevLogIndex() + 1);
      }

      // Iterate through entries and append them.
      for (RaftLogEntry entry : request.entries()) {
        final long index = ++lastLogIndex;

        // Get the last entry written to the log by the writer.
        final Indexed<RaftLogEntry> lastEntry = writer.getLastEntry();

        final boolean failedToAppend = tryToAppend(future, writer, reader, entry, index, lastEntry);
        if (failedToAppend) {
          return;
        }

        // If the last log index meets the commitIndex, break the append loop to avoid appending
        // uncommitted entries.
        if (!role().active() && index == commitIndex) {
          break;
        }
      }
    }

    // Set the first commit index.
    raft.setFirstCommitIndex(request.commitIndex());

    // Update the context commit and global indices.
    final long previousCommitIndex = raft.setCommitIndex(commitIndex);
    if (previousCommitIndex < commitIndex) {
      log.trace("Committed entries up to index {}", commitIndex);
      raft.getServiceManager().applyAll(commitIndex);
    }

    // Return a successful append response.
    succeedAppend(lastLogIndex, future);
  }

  private boolean tryToAppend(
      CompletableFuture<AppendResponse> future,
      RaftLogWriter writer,
      RaftLogReader reader,
      RaftLogEntry entry,
      long index,
      Indexed<RaftLogEntry> lastEntry) {
    boolean failedToAppend = false;
    if (lastEntry != null) {
      // If the last written entry index is greater than the next append entry index,
      // we need to validate that the entry that's already in the log matches this entry.
      if (lastEntry.index() > index) {
        failedToAppend = !replaceExistingEntry(future, writer, reader, entry, index);
      } else if (lastEntry.index() == index) {
        // If the last written entry is equal to the append entry index, we don't need
        // to read the entry from disk and can just compare the last entry in the writer.

        // If the last entry term doesn't match the leader's term for the same entry, truncate
        // the log and append the leader's entry.
        if (lastEntry.entry().term() != entry.term()) {
          writer.truncate(index - 1);
          failedToAppend = !appendEntry(index, entry, writer, future);
        }
      } else { // Otherwise, this entry is being appended at the end of the log.
        failedToAppend = !appendEntry(future, writer, entry, index, lastEntry);
      }
    } else { // Otherwise, if the last entry is null just append the entry and log a message.
      failedToAppend = !appendEntry(index, entry, writer, future);
    }
    return failedToAppend;
  }

  private boolean appendEntry(
      CompletableFuture<AppendResponse> future,
      RaftLogWriter writer,
      RaftLogEntry entry,
      long index,
      Indexed<RaftLogEntry> lastEntry) {
    // If the last entry index isn't the previous index, throw an exception because
    // something crazy happened!
    if (lastEntry.index() != index - 1) {
      throw new IllegalStateException(
          "Log writer inconsistent with next append entry index " + index);
    }

    // Append the entry and log a message.
    return appendEntry(index, entry, writer, future);
  }

  private boolean replaceExistingEntry(
      CompletableFuture<AppendResponse> future,
      RaftLogWriter writer,
      RaftLogReader reader,
      RaftLogEntry entry,
      long index) {
    // Reset the reader to the current entry index.
    if (reader.getNextIndex() != index) {
      reader.reset(index);
    }

    // If the reader does not have any next entry, that indicates an inconsistency between
    // the reader and writer.
    if (!reader.hasNext()) {
      throw new IllegalStateException("Log reader inconsistent with log writer");
    }

    // Read the existing entry from the log.
    final Indexed<RaftLogEntry> existingEntry = reader.next();

    // If the existing entry term doesn't match the leader's term for the same entry,
    // truncate
    // the log and append the leader's entry.
    if (existingEntry.entry().term() != entry.term()) {
      writer.truncate(index - 1);
      if (!appendEntry(index, entry, writer, future)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Attempts to append an entry, returning {@code false} if the append fails due to an {@link
   * StorageException.OutOfDiskSpace} exception.
   */
  private boolean appendEntry(
      long index,
      RaftLogEntry entry,
      RaftLogWriter writer,
      CompletableFuture<AppendResponse> future) {
    try {
      final Indexed<RaftLogEntry> indexed = writer.append(entry);
      log.trace("Appended {}", indexed);
    } catch (StorageException.TooLarge e) {
      log.warn(
          "Entry size exceeds maximum allowed bytes. Ensure Raft storage configuration is consistent on all nodes!");
      return false;
    } catch (StorageException.OutOfDiskSpace e) {
      log.trace("Append failed: {}", e);
      raft.getServiceManager().compact();
      failAppend(index - 1, future);
      return false;
    }
    return true;
  }

  /**
   * Returns a failed append response.
   *
   * @param lastLogIndex the last log index
   * @param future the append response future
   * @return the append response status
   */
  protected boolean failAppend(long lastLogIndex, CompletableFuture<AppendResponse> future) {
    return completeAppend(false, lastLogIndex, future);
  }

  /**
   * Returns a successful append response.
   *
   * @param lastLogIndex the last log index
   * @param future the append response future
   * @return the append response status
   */
  protected boolean succeedAppend(long lastLogIndex, CompletableFuture<AppendResponse> future) {
    return completeAppend(true, lastLogIndex, future);
  }

  /**
   * Returns a successful append response.
   *
   * @param succeeded whether the append succeeded
   * @param lastLogIndex the last log index
   * @param future the append response future
   * @return the append response status
   */
  protected boolean completeAppend(
      boolean succeeded, long lastLogIndex, CompletableFuture<AppendResponse> future) {
    future.complete(
        logResponse(
            AppendResponse.builder()
                .withStatus(RaftResponse.Status.OK)
                .withTerm(raft.getTerm())
                .withSucceeded(succeeded)
                .withLastLogIndex(lastLogIndex)
                .withLastSnapshotIndex(raft.getSnapshotStore().getCurrentSnapshotIndex())
                .build()));
    return succeeded;
  }

  /** Performs a local query. */
  protected CompletableFuture<QueryResponse> queryLocal(Indexed<QueryEntry> entry) {
    return applyQuery(entry);
  }
}
