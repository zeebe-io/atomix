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
package io.atomix.protocols.raft.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.impl.ClasspathScanningPrimitiveTypeRegistry;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.protocols.raft.RaftRoleChangeListener;
import io.atomix.protocols.raft.RaftServer;
import io.atomix.protocols.raft.cluster.RaftCluster;
import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.utils.concurrent.AtomixFuture;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Provides a standalone implementation of the <a href="http://raft.github.io/">Raft consensus
 * algorithm</a>.
 *
 * @see PrimitiveService
 * @see RaftStorage
 */
public class DefaultRaftServer implements RaftServer {

  protected final RaftContext context;
  private final Logger log;
  private volatile CompletableFuture<RaftServer> openFuture;
  private volatile CompletableFuture<Void> closeFuture;
  private volatile boolean started;

  public DefaultRaftServer(RaftContext context) {
    this.context = checkNotNull(context, "context cannot be null");
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(RaftServer.class).addValue(context.getName()).build());
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("name", name()).toString();
  }

  @Override
  public String name() {
    return context.getName();
  }

  @Override
  public RaftCluster cluster() {
    return context.getCluster();
  }

  @Override
  public void addRoleChangeListener(RaftRoleChangeListener listener) {
    context.addRoleChangeListener(listener);
  }

  @Override
  public void removeRoleChangeListener(RaftRoleChangeListener listener) {
    context.removeRoleChangeListener(listener);
  }

  @Override
  public void addFailureListener(Runnable failureListener) {
    context.addFailureListener(failureListener);
  }

  @Override
  public void removeFailureListener(Runnable failureListener) {
    context.removeFailureListener(failureListener);
  }

  @Override
  public CompletableFuture<RaftServer> bootstrap(Collection<MemberId> cluster) {
    return start(() -> cluster().bootstrap(cluster));
  }

  @Override
  public CompletableFuture<RaftServer> join(Collection<MemberId> cluster) {
    return start(() -> cluster().join(cluster));
  }

  @Override
  public CompletableFuture<RaftServer> listen(Collection<MemberId> cluster) {
    return start(() -> cluster().listen(cluster));
  }

  @Override
  public CompletableFuture<RaftServer> promote() {
    return context.anoint().thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> compact() {
    return context.compact();
  }

  /**
   * Shuts down the server without leaving the Raft cluster.
   *
   * @return A completable future to be completed once the server has been shutdown.
   */
  public CompletableFuture<Void> shutdown() {
    if (!started) {
      return Futures.exceptionalFuture(new IllegalStateException("Server not running"));
    }

    final CompletableFuture<Void> future = new AtomixFuture<>();
    context
        .getThreadContext()
        .execute(
            () -> {
              started = false;
              context.transition(Role.INACTIVE);
              future.complete(null);
            });

    return future.whenCompleteAsync(
        (result, error) -> {
          context.close();
          started = false;
        });
  }

  /**
   * Leaves the Raft cluster.
   *
   * @return A completable future to be completed once the server has left the cluster.
   */
  public CompletableFuture<Void> leave() {
    if (!started) {
      return CompletableFuture.completedFuture(null);
    }

    if (closeFuture == null) {
      synchronized (this) {
        if (closeFuture == null) {
          closeFuture = new AtomixFuture<>();
          if (openFuture == null) {
            cluster()
                .leave()
                .whenComplete(
                    (leaveResult, leaveError) -> {
                      shutdown()
                          .whenComplete(
                              (shutdownResult, shutdownError) -> {
                                context.delete();
                                closeFuture.complete(null);
                              });
                    });
          } else {
            leaveAfterOpenFinished();
          }
        }
      }
    }

    return closeFuture;
  }

  private void leaveAfterOpenFinished() {
    openFuture.whenComplete(
        (openResult, openError) -> {
          if (openError == null) {
            cluster()
                .leave()
                .whenComplete(
                    (leaveResult, leaveError) -> {
                      shutdown()
                          .whenComplete(
                              (shutdownResult, shutdownError) -> {
                                context.delete();
                                closeFuture.complete(null);
                              });
                    });
          } else {
            closeFuture.complete(null);
          }
        });
  }

  @Override
  public RaftContext getContext() {
    return context;
  }

  @Override
  public long getTerm() {
    return context.getTerm();
  }

  @Override
  public Role getRole() {
    return context.getRole();
  }

  /**
   * Returns a boolean indicating whether the server is running.
   *
   * @return Indicates whether the server is running.
   */
  public boolean isRunning() {
    return started && context.isRunning();
  }

  @Override
  public CompletableFuture<Void> stepDown() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    context
        .getThreadContext()
        .execute(
            () -> {
              context.transition(Role.FOLLOWER);
              future.complete(null);
            });
    return future;
  }

  /** Starts the server. */
  private CompletableFuture<RaftServer> start(Supplier<CompletableFuture<Void>> joiner) {
    if (started) {
      return CompletableFuture.completedFuture(this);
    }

    if (openFuture == null) {
      synchronized (this) {
        if (openFuture == null) {
          final CompletableFuture<RaftServer> future = new AtomixFuture<>();
          openFuture = future;
          joiner
              .get()
              .whenComplete(
                  (result, error) -> {
                    if (error == null) {
                      context.awaitState(
                          RaftContext.State.READY,
                          state -> {
                            started = true;
                            future.complete(null);
                          });
                    } else {
                      future.completeExceptionally(error);
                    }
                  });
        }
      }
    }

    return openFuture.whenComplete(
        (result, error) -> {
          if (error == null) {
            log.debug("Server started successfully!");
          } else {
            log.warn("Failed to start server!");
          }
        });
  }

  /** Default Raft server builder. */
  public static class Builder extends RaftServer.Builder {

    public Builder(MemberId localMemberId) {
      super(localMemberId);
    }

    @Override
    public RaftServer build() {
      final Logger log =
          ContextualLoggerFactory.getLogger(
              RaftServer.class, LoggerContext.builder(RaftServer.class).addValue(name).build());

      if (primitiveTypes == null) {
        primitiveTypes =
            new ClasspathScanningPrimitiveTypeRegistry(
                Thread.currentThread().getContextClassLoader());
      }
      if (primitiveTypes.getPrimitiveTypes().isEmpty()) {
        throw new IllegalStateException("No primitive services registered");
      }

      // If the server name is null, set it to the member ID.
      if (name == null) {
        name = localMemberId.id();
      }

      // If the storage is not configured, create a new Storage instance with the configured
      // serializer.
      if (storage == null) {
        storage = RaftStorage.builder().build();
      }

      // If a ThreadContextFactory was not provided, create one and ensure it's closed when the
      // server is stopped.
      final boolean closeOnStop;
      final ThreadContextFactory threadContextFactory;
      if (this.threadContextFactory == null) {
        threadContextFactory =
            threadModel.factory("raft-server-" + name + "-%d", threadPoolSize, log);
        closeOnStop = true;
      } else {
        threadContextFactory = this.threadContextFactory;
        closeOnStop = false;
      }

      final RaftContext raft =
          new RaftContext(
              name,
              localMemberId,
              membershipService,
              protocol,
              storage,
              primitiveTypes,
              threadContextFactory,
              closeOnStop,
              stateMachineFactory,
              loadMonitorFactory);
      raft.setElectionTimeout(electionTimeout);
      raft.setHeartbeatInterval(heartbeatInterval);
      raft.setSessionTimeout(sessionTimeout);

      return new DefaultRaftServer(raft);
    }
  }
}
