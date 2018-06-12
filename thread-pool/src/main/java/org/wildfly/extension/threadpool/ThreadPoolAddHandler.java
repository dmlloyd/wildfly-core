/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.wildfly.extension.threadpool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.management.StandardThreadPoolMXBean;

/**
 */
final class ThreadPoolAddHandler extends GeneralAddHandler {
    // all attributes which are used when the pool is defined
    final SimpleAttributeDefinition corePoolSizeAttr;
    final SimpleAttributeDefinition maxPoolSizeAttr;
    final SimpleAttributeDefinition threadNameAttr;
    final SimpleAttributeDefinition stackSizeAttr;
    final SimpleAttributeDefinition keepAliveAttr;

    ThreadPoolAddHandler(final SimpleAttributeDefinition corePoolSizeAttr, final SimpleAttributeDefinition maxPoolSizeAttr, final SimpleAttributeDefinition threadNameAttr, final SimpleAttributeDefinition stackSizeAttr, final SimpleAttributeDefinition keepAliveAttr) {
        super(corePoolSizeAttr, maxPoolSizeAttr, threadNameAttr, stackSizeAttr, keepAliveAttr);
        this.corePoolSizeAttr = corePoolSizeAttr;
        this.maxPoolSizeAttr = maxPoolSizeAttr;
        this.threadNameAttr = threadNameAttr;
        this.stackSizeAttr = stackSizeAttr;
        this.keepAliveAttr = keepAliveAttr;
    }

    protected Resource createResource(final OperationContext context, final ModelNode operation) {
        final Resource parentResource = Resource.Factory.create(context.getResourceRegistration().isRuntimeOnly());

        final AttachmentHolder<EnhancedQueueExecutor.Builder> builderHolder = new AttachmentHolder<>();
        final AttachmentHolder<StandardThreadPoolMXBean> mbeanHolder = new AttachmentHolder<>();
        final AttachmentHolder<ThreadFactoryBuilder> tfBuilderHolder = new AttachmentHolder<>();
        final AttachmentResource<EnhancedQueueExecutor.Builder> resource = new AttachmentResource<>(new AttachmentResource<>(new AttachmentResource<>(parentResource, tfBuilderHolder), mbeanHolder), builderHolder);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        return resource;
    }

    protected void performRuntime(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        // Basic information
        final ModelNode initialModel = resource.getModel();
        final PathAddress pathAddress = context.getCurrentAddress();
        final String name = pathAddress.getLastElement().getValue();

        // The builder is used for all run-time updates even if the service is *down*
        @SuppressWarnings("unchecked")
        final AttachmentHolder<EnhancedQueueExecutor.Builder> builderHolder = ((AttachmentResource<EnhancedQueueExecutor.Builder>) Utils.getDelegate(resource, 0)).getAttachmentHolder();
        final EnhancedQueueExecutor.Builder builder = new EnhancedQueueExecutor.Builder();

        // The mbean is used for non-service-restarting run-time updates while the service is *up*
        @SuppressWarnings("unchecked")
        final AttachmentHolder<StandardThreadPoolMXBean> mbeanHolder = ((AttachmentResource<StandardThreadPoolMXBean>) Utils.getDelegate(resource, 1)).getAttachmentHolder();

        // This builder is used for updates to the thread-factory config, which requires service restart
        @SuppressWarnings("unchecked")
        final AttachmentHolder<ThreadFactoryBuilder> tfBuilderHolder = ((AttachmentResource<ThreadFactoryBuilder>) Utils.getDelegate(resource, 2)).getAttachmentHolder();
        final ThreadFactoryBuilder tfBuilder = new ThreadFactoryBuilder("Thread pool " + name + "-%d");

        // this sync is necessary to avoid a race between manual service manipulation and container operations...
        // we can remove it if/when we get proper MSC transactions
        synchronized (builder) {
            // configure the initial thread factory
            tfBuilder.setThreadNamePattern(threadNameAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asString());
            tfBuilder.setStackSize(stackSizeAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asLongOrNull());
            tfBuilder.setThreadGroup(new ThreadGroup("group-" + name));
            tfBuilder.setExceptionHandler(ThreadPoolExtension.EXCEPTION_HANDLER);
            builder.setThreadFactory(tfBuilder.build());

            // configure the initial builder
            final int corePoolSize = corePoolSizeAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asInt();
            final int maxPoolSize = maxPoolSizeAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asInt();
            final Long keepAliveTime = keepAliveAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asLongOrNull();
            builder.setCorePoolSize(corePoolSize);
            builder.setMaximumPoolSize(maxPoolSize);
            if (keepAliveTime != null) builder.setKeepAliveTime(keepAliveTime.longValue(), TimeUnit.MILLISECONDS);
            builder.setMBeanName(name);
            builder.setRegisterMBean(true);
            builderHolder.set(builder);
            tfBuilderHolder.set(tfBuilder);
        }

        final ServiceBuilder<?> tpServiceBuilder = context.getServiceTarget().addService(ThreadPoolExtension.EXECUTOR_SERVICE_NAME.append(name));
        final Consumer<ExecutorService> sesConsumer = tpServiceBuilder.provides(ThreadPoolExtension.EXECUTOR_CAP_SERVICE_NAME.append(name));
        tpServiceBuilder.setInstance(new ThreadPoolService(builderHolder, sesConsumer, mbeanHolder));
        tpServiceBuilder.install();
    }
}
