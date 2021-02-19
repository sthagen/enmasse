/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.integration.amqp;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ConnectorService;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.client.ProtonClientProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASLFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.qpid.proton.amqp.Symbol;

/**
 * Connector service for outgoing AMQP connections.
 */
public class AMQPConnectorService implements ConnectorService, BaseConnectionLifeCycleListener<ProtonProtocolManager> {
   private static final Symbol groupSymbol = Symbol.getSymbol("qd.route-container-group");
   private final String name;
   private final ActiveMQServer server;
   private volatile RemotingConnection connection;
   private final ExecutorService closeExecutor = ThreadPoolExecutor.newSingleThreadExecutor(new DefaultThreadFactory(AMQPConnectorService.class));
   private final ExecutorService nettyThreadPool;
   private final ScheduledExecutorService scheduledExecutorService;
   private final ProtonClientConnectionManager lifecycleHandler;
   private volatile boolean started = false;
   private volatile ScheduledFuture<?> connectionCheckFuture;
   private final Map<String, Object> connectorConfig;
   private final int idleTimeout;
   private final boolean treatRejectAsUnmodifiedDeliveryFailed;
   private final boolean useModifiedForTransientDeliveryErrors;
   private final int minLargeMessageSize;

   public AMQPConnectorService(String connectorName, Map<String, Object> connectorConfig, String containerId, String groupId, Optional<LinkInfo> linkInfo, ActiveMQServer server, ScheduledExecutorService scheduledExecutorService, ExecutorService nettyThreadPool, int idleTimeout, boolean treatRejectAsUnmodifiedDeliveryFailed, boolean useModifiedForTransientDeliveryErrors, int minLargeMessageSize) {
      this.name = connectorName;
      this.connectorConfig = connectorConfig;
      this.server = server;
      this.scheduledExecutorService = scheduledExecutorService;
      this.nettyThreadPool = nettyThreadPool;
      this.idleTimeout = idleTimeout;
      this.treatRejectAsUnmodifiedDeliveryFailed = treatRejectAsUnmodifiedDeliveryFailed;
      this.useModifiedForTransientDeliveryErrors = useModifiedForTransientDeliveryErrors;
      this.minLargeMessageSize = minLargeMessageSize;
      AMQPClientConnectionFactory factory = new AMQPClientConnectionFactory(server, containerId, Collections.singletonMap(groupSymbol, groupId), idleTimeout);
      boolean sslEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.SSL_ENABLED_PROP_NAME, TransportConstants.DEFAULT_SSL_ENABLED, connectorConfig);

      ClientSASLFactory saslClientFactory = null;
      if (sslEnabled) {
         ActiveMQAMQPLogger.LOGGER.infov("Enabling SSL for AMQP Connector {0}", name);
         saslClientFactory = availableMechanims -> {
            if (Arrays.asList(availableMechanims).contains("EXTERNAL")) {
               return new ClientSASL() {
                  @Override
                  public String getName() {
                     return "EXTERNAL";
                  }

                  @Override
                  public byte[] getInitialResponse() {
                     return new byte[0];
                  }

                  @Override
                  public byte[] getResponse(final byte[] challenge) {
                     return new byte[0];
                  }
               };
            } else {
               return null;
            }
         };
      } else  {
         ActiveMQAMQPLogger.LOGGER.infov("Disabling SSL for AMQP Connector {0}", name);
      }
      this.lifecycleHandler = new ProtonClientConnectionManager(factory, linkInfo.map(LinkInitiator::new), saslClientFactory);
   }

   @Override
   public void start() throws Exception {

      scheduledExecutorService.submit(() -> {
         Connection connection;
         try {
            ActiveMQAMQPLogger.LOGGER.infov("Starting connector {0}", name);
            ProtonClientProtocolManager protocolManager = new ProtonClientProtocolManager(new ProtonProtocolManagerFactory(), server);

            // These settings have the desired semantics when working in a cloud environment and for the built-in message
            // forwarding.
            protocolManager.setAmqpTreatRejectAsUnmodifiedDeliveryFailed(treatRejectAsUnmodifiedDeliveryFailed);
            protocolManager.setAmqpUseModifiedForTransientDeliveryErrors(useModifiedForTransientDeliveryErrors);
            protocolManager.setAmqpMinLargeMessageSize(minLargeMessageSize);

            NettyConnector connector = new NettyConnector(connectorConfig, lifecycleHandler, AMQPConnectorService.this, closeExecutor, nettyThreadPool, server.getScheduledPool(), protocolManager);
            connector.start();
            connection = connector.createConnection();
         } catch (Throwable t) {
            // Note - the scheduledExecutorService supplied by Artemis ignores exceptions.  Ensure the cause of the failure is logged..
            ActiveMQAMQPLogger.LOGGER.errorv(t, "Error starting connector {0}", name);
            throw t;
         }

         if (connection != null) {
            started = true;
            if (idleTimeout > 0 && connectionCheckFuture == null) {
               long connCheckTimeout = idleTimeout * 2;
               connectionCheckFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
                          ActiveMQAMQPLogger.LOGGER.infov("Checking connections on connector {0}", name);
                          lifecycleHandler.checkIdleConnections(connCheckTimeout);
                       },
                       connCheckTimeout,
                       connCheckTimeout,
                       TimeUnit.MILLISECONDS);
            }
         } else {
            ActiveMQAMQPLogger.LOGGER.infov("Error starting connector {0}, retrying in 5 seconds", name);
            scheduledExecutorService.schedule(() -> {
               start();
               return true;
            }, 5, TimeUnit.SECONDS);
         }
      });
   }

   @Override
   public void stop() throws Exception {
      scheduledExecutorService.submit(() -> {
         try {
            started = false;
            ActiveMQAMQPLogger.LOGGER.infov("Stopping connector {0}", name);
            if (connection != null) {
               lifecycleHandler.stop();
            }
            closeExecutor.shutdown();
            nettyThreadPool.shutdown();
            ActiveMQAMQPLogger.LOGGER.infov("Stopped connector {0}", name);

            if (connectionCheckFuture != null) {
               connectionCheckFuture.cancel(false);
               connectionCheckFuture = null;
            }
         } catch (Throwable t) {
            // Note - the scheduledExecutorService supplied by Artemis ignores exceptions.  Ensure the cause of the failure is logged..
            ActiveMQAMQPLogger.LOGGER.errorv(t, "Error stopping connector {0}", name);
            throw t;
         }
      });
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public void connectionCreated(ActiveMQComponent component, Connection connection, ProtonProtocolManager protocol) {
      ActiveMQAMQPLogger.LOGGER.infov("connectionCreated for connector {0}", name);
      lifecycleHandler.connectionCreated(component, connection, protocol);
      this.connection = connection.getProtocolConnection();
   }

   @Override
   public void connectionDestroyed(Object connectionID) {
      lifecycleHandler.connectionDestroyed(connectionID);
      ActiveMQAMQPLogger.LOGGER.infov("connectionDestroyed for connector {0}", name);
      if (started) {
         scheduledExecutorService.schedule(() -> {
            start();
            return true;
         }, 5, TimeUnit.SECONDS);
      }
   }

   @Override
   public void connectionException(Object connectionID, ActiveMQException me) {
      ActiveMQAMQPLogger.LOGGER.infov("connectionException for connector {0}: {1}", name, me.getMessage());
      lifecycleHandler.connectionException(connectionID, me);
   }

   @Override
   public void connectionReadyForWrites(Object connectionID, boolean ready) {
      ActiveMQAMQPLogger.LOGGER.debugv("connectionReadyForWrites for connector {0}", name);
      lifecycleHandler.connectionReadyForWrites(connectionID, ready);
   }
}
