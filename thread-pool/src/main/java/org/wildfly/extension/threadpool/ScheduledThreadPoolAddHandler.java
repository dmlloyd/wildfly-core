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

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.threads.management.ThreadPoolExecutorMBean;

/**
 */
final class ScheduledThreadPoolAddHandler extends GeneralAddHandler {
    final SimpleAttributeDefinition maxPoolSizeAttr;
    final SimpleAttributeDefinition threadNameAttr;
    final SimpleAttributeDefinition stackSizeAttr;
    final SimpleAttributeDefinition keepAliveAttr;

    ScheduledThreadPoolAddHandler(final SimpleAttributeDefinition maxPoolSizeAttr, final SimpleAttributeDefinition threadNameAttr, final SimpleAttributeDefinition stackSizeAttr, final SimpleAttributeDefinition keepAliveAttr) {
        super(maxPoolSizeAttr, threadNameAttr, stackSizeAttr, keepAliveAttr);
        this.maxPoolSizeAttr = maxPoolSizeAttr;
        this.threadNameAttr = threadNameAttr;
        this.stackSizeAttr = stackSizeAttr;
        this.keepAliveAttr = keepAliveAttr;
    }

    protected Resource createResource(final OperationContext context, final ModelNode operation) {
        final Resource parentResource = Resource.Factory.create(context.getResourceRegistration().isRuntimeOnly());

        final AttachmentHolder<ScheduledThreadPoolBuilder> builderHolder = new AttachmentHolder<>();
        @SuppressWarnings("deprecation")
        final AttachmentHolder<ThreadPoolExecutorMBean> mbeanHolder = new AttachmentHolder<>();
        final AttachmentHolder<ThreadFactoryBuilder> tfBuilderHolder = new AttachmentHolder<>();
        final AttachmentResource<ScheduledThreadPoolBuilder> resource = new AttachmentResource<>(new AttachmentResource<>(new AttachmentResource<>(parentResource, tfBuilderHolder), mbeanHolder), builderHolder);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        return resource;
    }

    protected void performRuntime(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        // Basic information
        final PathAddress pathAddress = context.getCurrentAddress();
        final ModelNode initialModel = resource.getModel();
        final String name = pathAddress.getLastElement().getValue();

        // The builder is used for all run-time updates even if the service is *down*
        @SuppressWarnings("unchecked")
        final AttachmentHolder<ScheduledThreadPoolBuilder> builderHolder = ((AttachmentResource<ScheduledThreadPoolBuilder>) Utils.getDelegate(resource, 0)).getAttachmentHolder();
        final ScheduledThreadPoolBuilder builder = new ScheduledThreadPoolBuilder();

        // The mbean is used for non-service-restarting run-time updates while the service is *up*
        @SuppressWarnings({"unchecked", "deprecation"})
        final AttachmentHolder<ThreadPoolExecutorMBean> mbeanHolder = ((AttachmentResource<ThreadPoolExecutorMBean>) Utils.getDelegate(resource, 1)).getAttachmentHolder();

        // This builder is used for updates to the thread-factory config, which requires service restart
        @SuppressWarnings("unchecked")
        final AttachmentHolder<ThreadFactoryBuilder> tfBuilderHolder = ((AttachmentResource<ThreadFactoryBuilder>) Utils.getDelegate(resource, 2)).getAttachmentHolder();
        final ThreadFactoryBuilder tfBuilder = new ThreadFactoryBuilder("Scheduled thread pool " + name + "-%d");

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
            final int maxPoolSize = maxPoolSizeAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asInt();
            final Long keepAliveTime = keepAliveAttr.resolveModelAttribute(ExpressionResolver.SIMPLE, initialModel).asLongOrNull();
            builder.setPoolSize(maxPoolSize);
            builder.setKeepAliveTime(keepAliveTime == null ? Long.MAX_VALUE : keepAliveTime.longValue());
            builderHolder.set(builder);
            tfBuilderHolder.set(tfBuilder);
        }


        final ServiceBuilder<?> serviceBuilder = context.getServiceTarget().addService(ThreadPoolExtension.SCHEDULED_EXECUTOR_SERVICE_NAME.append(name));
        final Consumer<ScheduledExecutorService> sesConsumer = serviceBuilder.provides(ThreadPoolExtension.SCHEDULED_EXECUTOR_CAP_SERVICE_NAME.append(name));
        serviceBuilder.setInstance(new ScheduledThreadPoolService(builderHolder, sesConsumer, mbeanHolder));
        serviceBuilder.install();
    }
}
