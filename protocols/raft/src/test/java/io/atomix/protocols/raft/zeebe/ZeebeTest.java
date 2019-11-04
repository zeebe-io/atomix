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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.google.common.base.Stopwatch;
import io.atomix.protocols.raft.RaftCommitListener;
import io.atomix.protocols.raft.partition.impl.RaftPartitionServer;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.protocols.raft.zeebe.util.ZeebeTestHelper;
import io.atomix.protocols.raft.zeebe.util.ZeebeTestNode;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.concurrent.Futures;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class ZeebeTest {

  // rough estimate of how many entries we'd need to write to fill a segment
  // segments are configured for 1kb, and one entry takes ~20 bytes (plus some metadata I guess)
  private static final int ENTRIES_PER_SEGMENT = (1024 / 20) + 1;

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Collection<Function<TemporaryFolder, ZeebeTestNode>> nodeSuppliers;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final Stopwatch stopwatch = Stopwatch.createUnstarted();
  private Collection<ZeebeTestNode> nodes;
  private ZeebeTestHelper helper;

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[] {"single node", Collections.singleton(provideNode(1))},
        new Object[] {
          "three nodes", Arrays.asList(provideNode(1), provideNode(2), provideNode(3))
        });
  }

  @Before
  public void setUp() throws Exception {
    stopwatch.reset();
    nodes = buildNodes();
    helper = new ZeebeTestHelper(nodes);
    start();

    stopwatch.start();
  }

  @After
  public void tearDown() throws Exception {
    if (stopwatch.isRunning()) {
      stopwatch.stop();
    }

    logger.info("Test run time: {}", stopwatch.toString());
    stop();
  }

  private Collection<ZeebeTestNode> buildNodes() {
    return nodeSuppliers.stream()
        .map(supplier -> supplier.apply(temporaryFolder))
        .collect(Collectors.toList());
  }

  @SuppressWarnings("squid:S2699") // awaitAllContain is the assert here
  @Test
  public void shouldAppendAndReplicate() {
    // given
    final int partitionId = 1;
    final ZeebeLogAppender appender = helper.awaitLeaderAppender(partitionId);

    // when
    final Indexed<ZeebeEntry> appended = appender.appendEntry(getIntAsBytes(0)).join();

    // then
    helper.awaitAllContain(partitionId, appended);
  }

  @Test
  public void shouldNotCompactAnything() {
    // given
    final int partitionId = 1;
    final RaftPartitionServer server = helper.awaitLeaderServer(1);
    final ZeebeLogAppender appender = helper.awaitLeaderAppender(partitionId);

    // when
    final Indexed<ZeebeEntry> firstAppended = appender.appendEntry(getIntAsBytes(0)).join();
    for (int i = 1; i < ENTRIES_PER_SEGMENT; i++) {
      helper.awaitAllContain(partitionId, appender.appendEntry(getIntAsBytes(i)).join());
    }
    server.snapshot().join();

    // then
    assertTrue(helper.containsIndexed(server, firstAppended));
  }

  @Test
  public void shouldCompactUpToCompactablePosition() {
    // given
    final int partitionId = 1;
    final RaftPartitionServer server = helper.awaitLeaderServer(1);
    final ZeebeLogAppender appender = helper.awaitLeaderAppender(partitionId);

    // when
    Indexed<ZeebeEntry> appended = appender.appendEntry(getIntAsBytes(0)).join();
    final Indexed<ZeebeEntry> firstAppended = appended;
    for (int i = 1; i < ENTRIES_PER_SEGMENT; i++) {
      appended = appender.appendEntry(getIntAsBytes(i)).join();
      helper.awaitAllContain(partitionId, appended);
    }
    server.setCompactablePosition(appended.index(), appended.entry().term());
    server.snapshot().join();

    // then
    assertFalse(helper.containsIndexed(server, firstAppended));
    assertTrue(helper.containsIndexed(server, appended));
  }

  @Test
  public void shouldFailover() {
    assumeTrue(nodes.size() > 1);

    // given
    final int partitionId = 1;
    final ZeebeTestNode originalLeader = helper.awaitLeader(partitionId);

    // when
    originalLeader.stop().join();
    originalLeader.start(nodes).join();

    // then
    assertTrue(nodes.size() == 1 || !originalLeader.equals(helper.awaitLeader(partitionId)));
  }

  @SuppressWarnings("squid:S2699") // awaitAllContain is the assert here
  @Test
  public void shouldAppendAllEntriesEvenWithFollowerFailures() {
    assumeTrue(nodes.size() > 1);

    // given
    final int partitionId = 1;
    final ZeebeTestNode leader = helper.awaitLeader(partitionId);
    final ZeebeLogAppender appender = helper.awaitLeaderAppender(partitionId);
    final List<ZeebeTestNode> followers =
        nodes.stream().filter(node -> !node.equals(leader)).collect(Collectors.toList());
    final List<Indexed<ZeebeEntry>> entries = new ArrayList<>();

    // when
    for (int i = 0; i < followers.size(); i++) {
      final ZeebeTestNode follower = followers.get(i);
      final List<ZeebeTestNode> others =
          nodes.stream().filter(node -> !node.equals(follower)).collect(Collectors.toList());
      follower.stop().join();

      entries.add(i, appender.appendEntry(getIntAsBytes(i)).join());
      helper.awaitAllContains(others, partitionId, entries.get(i));
      follower.start(nodes).join();
    }

    // then
    for (final Indexed<ZeebeEntry> entry : entries) {
      helper.awaitAllContain(partitionId, entry);
    }
  }

  @Test
  public void shouldNotifyCommitListeners() {
    // given
    final int partitionId = 1;
    final ZeebeLogAppender appender = helper.awaitLeaderAppender(partitionId);
    final Map<ZeebeTestNode, CommitListener> listeners =
        nodes.stream()
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    node -> {
                      final CommitListener listener = new CommitListener();
                      node.getPartitionServer(partitionId).addCommitListener(listener);
                      return listener;
                    }));

    // when - then
    for (int i = 0; i < 5; i++) {
      final Indexed<ZeebeEntry> entry = appender.appendEntry(getIntAsBytes(i)).join();
      helper.awaitAllContains(nodes, partitionId, entry);

      for (final ZeebeTestNode node : nodes) {
        final CommitListener listener = listeners.get(node);
        assertEquals(i + 1, listener.calledCount.get());
        assertTrue(helper.isEntryEqualTo(entry, listener.lastCommitted.get()));
      }
    }
  }

  private static Function<TemporaryFolder, ZeebeTestNode> provideNode(final int id) {
    return tmp -> new ZeebeTestNode(id, newFolderUnchecked(tmp, id));
  }

  private static File newFolderUnchecked(final TemporaryFolder tmp, final int id) {
    try {
      return tmp.newFolder(String.valueOf(id));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void start() throws ExecutionException, InterruptedException, TimeoutException {
    Futures.allOf(nodes.stream().map(n -> n.start(nodes))).get(30, TimeUnit.SECONDS);
  }

  private void stop() throws InterruptedException, ExecutionException, TimeoutException {
    Futures.allOf(nodes.stream().map(ZeebeTestNode::stop)).get(30, TimeUnit.SECONDS);
  }

  private byte[] getIntAsBytes(final int value) {
    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
  }

  static class CommitListener implements RaftCommitListener {
    private final AtomicReference<Indexed<ZeebeEntry>> lastCommitted = new AtomicReference<>();
    private final AtomicInteger calledCount = new AtomicInteger(0);

    @Override
    public <T extends RaftLogEntry> void onCommit(final Indexed<T> entry) {
      if (entry.type() == ZeebeEntry.class) {
        lastCommitted.set(entry.cast());
        calledCount.incrementAndGet();
      }
    }
  }
}
