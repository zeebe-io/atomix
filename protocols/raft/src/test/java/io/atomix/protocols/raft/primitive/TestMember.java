package io.atomix.protocols.raft.primitive;

import io.atomix.cluster.MemberId;
import io.atomix.protocols.raft.cluster.RaftMember;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test member. */
public class TestMember implements RaftMember {

  private final MemberId memberId;
  private final Type type;

  public TestMember(MemberId memberId, Type type) {
    this.memberId = memberId;
    this.type = type;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public int hash() {
    return 0;
  }

  @Override
  public void addTypeChangeListener(Consumer<Type> listener) {}

  @Override
  public void removeTypeChangeListener(Consumer<Type> listener) {}

  @Override
  public CompletableFuture<Void> promote() {
    return null;
  }

  @Override
  public CompletableFuture<Void> promote(Type type) {
    return null;
  }

  @Override
  public CompletableFuture<Void> demote() {
    return null;
  }

  @Override
  public CompletableFuture<Void> demote(Type type) {
    return null;
  }

  @Override
  public CompletableFuture<Void> remove() {
    return null;
  }

  @Override
  public Instant getLastUpdated() {
    return null;
  }

  @Override
  public Type getType() {
    return type;
  }
}
