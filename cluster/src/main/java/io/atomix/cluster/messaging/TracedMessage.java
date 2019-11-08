package io.atomix.cluster.messaging;

import java.util.Map;

public interface TracedMessage {
  Map<String, String> getSpanContext();
}
