/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.threadpool;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.jboss.threads.JBossExecutors;

/**
 *
 */
public final class ThreadPoolExtension implements Extension {
    public static final String SUBSYSTEM_NAME = "thread-pool";

    static final Thread.UncaughtExceptionHandler EXCEPTION_HANDLER = JBossExecutors.loggingExceptionHandler("org.wildfly.thread-pool.exception");

    static final ModelVersion CURRENT = ModelVersion.create(1, 0);

    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);

    static final ServiceName BASE_SUBSYSTEM_SERVICE_NAME = ServiceName.JBOSS.append("threadpool");
    static final ServiceName EXECUTOR_SERVICE_NAME = BASE_SUBSYSTEM_SERVICE_NAME.append("executor");
    static final ServiceName SCHEDULED_EXECUTOR_SERVICE_NAME = EXECUTOR_SERVICE_NAME.append("scheduled");
    static final ServiceName EXECUTOR_CAP_SERVICE_NAME = ServiceName.JBOSS.append("capability", "executor");
    static final ServiceName SCHEDULED_EXECUTOR_CAP_SERVICE_NAME = EXECUTOR_CAP_SERVICE_NAME.append("scheduled");

    static final String CORE_THREADS = "core-threads";
    static final String MAX_THREADS = "max-threads";
    static final String THREAD_NAME_PATTERN = "thread-name-pattern";
    static final String STACK_SIZE = "stack-size";
    static final String KEEP_ALIVE = "keep-alive";

    static final String THREAD_POOL = "thread-pool";
    static final String SCHEDULED_THREAD_POOL = "scheduled-thread-pool";

    static final String XML_NAMESPACE_1_0 = "urn:wildfly:thread-pool:1.0";

    private volatile PersistentResourceXMLParser parser;

    // the absolute limit is 1048575L for EQE and Integer.MAX_VALUE for others, but we're going to limit to 2^17 for now.
    static final SimpleAttributeDefinition corePoolSizeAttr = SimpleAttributeDefinitionBuilder.create(CORE_THREADS, ModelType.INT)
        .setRequired(true)
        .setValidator(new IntRangeValidator(1, 1 << 17, false, true))
        .setAllowExpression(true)
        .build();

    // the absolute limit is 1048575L for EQE and Integer.MAX_VALUE for others, but we're going to limit to 2^17 for now.
    static final SimpleAttributeDefinition maxPoolSizeAttr = SimpleAttributeDefinitionBuilder.create(MAX_THREADS, ModelType.INT)
        .setRequired(true)
        .setValidator(new IntRangeValidator(1, 1 << 17, false, true))
        .setAllowExpression(true).build();

    static final SimpleAttributeDefinition threadNameAttr = SimpleAttributeDefinitionBuilder.create(THREAD_NAME_PATTERN, ModelType.STRING)
        .setRequired(false)
        .setAllowExpression(true)
        .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES).build();

    static final SimpleAttributeDefinition stackSizeAttr = SimpleAttributeDefinitionBuilder.create(STACK_SIZE, ModelType.LONG)
        .setRequired(false)
        .setAllowExpression(true)
        .setValidator(new LongRangeValidator(1L))
        .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES).build();

    static final SimpleAttributeDefinition keepAliveAttr = SimpleAttributeDefinitionBuilder.create(KEEP_ALIVE, ModelType.LONG)
        .setRequired(false)
        .setAllowExpression(true)
        .setValidator(new LongRangeValidator(1L)).build();

    public void initialize(final ExtensionContext context) {

        // Capabilities
        final RuntimeCapability<Void> execSvcCap;
        {
            final RuntimeCapability.Builder<Void> execSvcCapBuilder = RuntimeCapability.Builder.of("org.wildfly.executor", true, ExecutorService.class);
            execSvcCap = execSvcCapBuilder.build();
        }

        final RuntimeCapability<Void> schedExecSvcCap;
        {
            final RuntimeCapability.Builder<Void> schedExecSvcCapBuilder = RuntimeCapability.Builder.of("org.wildfly.executor.scheduled", true, ScheduledExecutorService.class);
            schedExecSvcCap = schedExecSvcCapBuilder.build();
        }

        final SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT);
        // TODO: because of I/O subsystem?
        // subsystemRegistration.setHostCapable();

        final StandardResourceDescriptionResolver rootResolver = new StandardResourceDescriptionResolver("subsystem", "org.wildfly.extension.threadpool.LocalDescriptions", getClass().getClassLoader(), true, false);

        final ManagementResourceRegistration rootRegistration;
        {
            final SimpleResourceDefinition.Parameters rootParameters = new SimpleResourceDefinition.Parameters(SUBSYSTEM_PATH, rootResolver);
            rootParameters.setAddHandler(new AbstractAddStepHandler());
            rootParameters.setRemoveHandler(new ModelOnlyRemoveStepHandler());
            final SimpleResourceDefinition rootDefinition = new SimpleResourceDefinition(rootParameters);
            rootRegistration = subsystemRegistration.registerSubsystemModel(rootDefinition);
            rootRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        }

        final ThreadFactoryWriteHandler threadFactoryWriteHandler = new ThreadFactoryWriteHandler(threadNameAttr, stackSizeAttr);

        {
            final StandardResourceDescriptionResolver tpResolver = rootResolver.getChildResolver(THREAD_POOL);
            final SimpleResourceDefinition.Parameters tpParameters = new SimpleResourceDefinition.Parameters(PathElement.pathElement(THREAD_POOL), tpResolver);
            final GeneralAddHandler addHandler = new ThreadPoolAddHandler(corePoolSizeAttr, maxPoolSizeAttr, threadNameAttr, stackSizeAttr, keepAliveAttr);
            tpParameters.setAddHandler(addHandler);
            tpParameters.setRemoveHandler(new GeneralRemoveHandler(EXECUTOR_SERVICE_NAME, addHandler));
            final SimpleResourceDefinition tpDefinition = new SimpleResourceDefinition(tpParameters);
            final ManagementResourceRegistration tpRegistration = rootRegistration.registerSubModel(tpDefinition);
            final ThreadPoolWriteHandler eqeWriteHandler = new ThreadPoolWriteHandler(corePoolSizeAttr, maxPoolSizeAttr, keepAliveAttr);
            tpRegistration.registerReadWriteAttribute(corePoolSizeAttr, null, eqeWriteHandler);
            tpRegistration.registerReadWriteAttribute(maxPoolSizeAttr, null, eqeWriteHandler);
            tpRegistration.registerReadWriteAttribute(keepAliveAttr, null, eqeWriteHandler);
            tpRegistration.registerReadWriteAttribute(threadNameAttr, null, threadFactoryWriteHandler);
            tpRegistration.registerReadWriteAttribute(stackSizeAttr, null, threadFactoryWriteHandler);
            tpRegistration.registerCapability(execSvcCap);
        }

        {
            final StandardResourceDescriptionResolver stpResolver = rootResolver.getChildResolver(SCHEDULED_THREAD_POOL);
            final SimpleResourceDefinition.Parameters stpParameters = new SimpleResourceDefinition.Parameters(PathElement.pathElement(SCHEDULED_THREAD_POOL), stpResolver);
            final GeneralAddHandler addHandler = new ScheduledThreadPoolAddHandler(maxPoolSizeAttr, threadNameAttr, stackSizeAttr, keepAliveAttr);
            stpParameters.setAddHandler(addHandler);
            stpParameters.setRemoveHandler(new GeneralRemoveHandler(SCHEDULED_EXECUTOR_SERVICE_NAME, addHandler));
            final SimpleResourceDefinition stpDefinition = new SimpleResourceDefinition(stpParameters);
            final ManagementResourceRegistration stpRegistration = rootRegistration.registerSubModel(stpDefinition);
            final ScheduledThreadPoolWriteHandler stpWriteHandler = new ScheduledThreadPoolWriteHandler(maxPoolSizeAttr, keepAliveAttr);
            stpRegistration.registerReadWriteAttribute(maxPoolSizeAttr, null, stpWriteHandler);
            stpRegistration.registerReadWriteAttribute(keepAliveAttr, null, stpWriteHandler);
            stpRegistration.registerReadWriteAttribute(threadNameAttr, null, threadFactoryWriteHandler);
            stpRegistration.registerReadWriteAttribute(stackSizeAttr, null, threadFactoryWriteHandler);
            stpRegistration.registerCapability(execSvcCap);
            stpRegistration.registerCapability(schedExecSvcCap);
        }
        subsystemRegistration.registerXMLElementWriter(getParser());
    }

    public void initializeParsers(final ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, XML_NAMESPACE_1_0, getParser());
    }

    private PersistentResourceXMLParser getParser() {
        PersistentResourceXMLParser parser = this.parser;
        if (parser == null) {
            synchronized (this) {
                parser = this.parser;
                if (parser == null) {
                    this.parser = parser = new PersistentResourceXMLParser() {
                        public PersistentResourceXMLDescription getParserDescription() {
                            return PersistentResourceXMLDescription.builder(SUBSYSTEM_PATH, XML_NAMESPACE_1_0)
                                .addChild(
                                    PersistentResourceXMLDescription.builder(PathElement.pathElement(THREAD_POOL))
                                        .addAttribute(corePoolSizeAttr)
                                        .addAttribute(maxPoolSizeAttr)
                                        .addAttribute(keepAliveAttr)
                                        .addAttribute(threadNameAttr)
                                        .addAttribute(stackSizeAttr)
                                        .build())
                                .addChild(
                                    PersistentResourceXMLDescription.builder(PathElement.pathElement(SCHEDULED_THREAD_POOL))
                                        .addAttribute(maxPoolSizeAttr)
                                        .addAttribute(keepAliveAttr)
                                        .addAttribute(threadNameAttr)
                                        .addAttribute(stackSizeAttr)
                                        .build())
                                .build();
                        }
                    };
                }
            }
        }
        return parser;
    }
}
