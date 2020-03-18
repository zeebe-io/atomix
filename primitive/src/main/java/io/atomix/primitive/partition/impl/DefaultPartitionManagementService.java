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
package io.atomix.primitive.partition.impl;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.PrimitiveTypeRegistry;
import io.atomix.primitive.partition.PartitionManagementService;

/**
 * Default partition management service.
 */
public class DefaultPartitionManagementService implements PartitionManagementService {

  private final ClusterMembershipService membershipService;
  private final ClusterCommunicationService communicationService;
  private final PrimitiveTypeRegistry primitiveTypes;

  public DefaultPartitionManagementService(
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final PrimitiveTypeRegistry primitiveTypes) {
    this.membershipService = membershipService;
    this.communicationService = communicationService;
    this.primitiveTypes = primitiveTypes;
  }

  @Override
  public ClusterMembershipService getMembershipService() {
    return membershipService;
  }

  @Override
  public ClusterCommunicationService getMessagingService() {
    return communicationService;
  }

  @Override
  public PrimitiveTypeRegistry getPrimitiveTypes() {
    return primitiveTypes;
  }

}
