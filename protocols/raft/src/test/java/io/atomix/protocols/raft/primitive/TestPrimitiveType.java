package io.atomix.protocols.raft.primitive;

import io.atomix.primitive.PrimitiveBuilder;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;

public class TestPrimitiveType implements PrimitiveType {

  public static final TestPrimitiveType INSTANCE = new TestPrimitiveType();

  @Override
  public String name() {
    return "raft-test";
  }

  @Override
  public PrimitiveConfig newConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveBuilder newBuilder(
      String primitiveName, PrimitiveConfig config, PrimitiveManagementService managementService) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveService newService(ServiceConfig config) {
    return new TestPrimitiveServiceImpl(config);
  }
}
