package io.atomix.protocols.raft.primitive;

import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.protocols.raft.impl.RaftServiceManager;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import java.time.Duration;

public class FakeStateMachine extends RaftServiceManager {
  public FakeStateMachine(final RaftContext context, final ThreadContext threadContext, final ThreadContextFactory threadContextFactory) {
    super(context, threadContext, threadContextFactory);
  }

  @Override
  protected Duration getSnapshotInterval() {
    return Duration.ofMillis(10);
  }

  @Override
  protected Duration getSnapshotCompletionDelay() {
    return Duration.ZERO;
  }

  @Override
  protected Duration getCompactDelay() {
    return Duration.ZERO;
  }
}
