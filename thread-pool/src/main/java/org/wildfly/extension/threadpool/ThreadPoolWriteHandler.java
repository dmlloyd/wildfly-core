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

import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.management.StandardThreadPoolMXBean;

/**
 */
final class ThreadPoolWriteHandler extends AbstractWriteAttributeHandler<Void> {
    ThreadPoolWriteHandler(final SimpleAttributeDefinition corePoolSizeAttr, final SimpleAttributeDefinition maxPoolSizeAttr, final SimpleAttributeDefinition keepAliveAttr) {
        super(corePoolSizeAttr, maxPoolSizeAttr, keepAliveAttr);
    }

    protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<Void> handbackHolder) {
        applyUpdate(context, attributeName, resolvedValue);
        return false;
    }

    protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final Void handback) {
        applyUpdate(context, attributeName, valueToRestore);
    }

    private void applyUpdate(final OperationContext context, final String attributeName, final ModelNode resolvedValue) {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        // The builder is used for all run-time updates even if the service is *down*
        @SuppressWarnings("unchecked")
        final AttachmentHolder<EnhancedQueueExecutor.Builder> builderHolder = ((AttachmentResource<EnhancedQueueExecutor.Builder>) Utils.getDelegate(resource, 0)).getAttachmentHolder();
        final EnhancedQueueExecutor.Builder builder = builderHolder.get();

        // The mbean is used for non-service-restarting run-time updates while the service is *up*
        @SuppressWarnings("unchecked")
        final AttachmentHolder<StandardThreadPoolMXBean> mbeanHolder = ((AttachmentResource<StandardThreadPoolMXBean>) Utils.getDelegate(resource, 1)).getAttachmentHolder();
        // this sync is necessary to avoid a race between manual service manipulation and container operations...
        // we can remove it if/when we get proper MSC transactions
        synchronized (builder) {
            final StandardThreadPoolMXBean mbean = mbeanHolder.get();
            switch (attributeName) {
                case ThreadPoolExtension.CORE_THREADS: {
                    final int val = resolvedValue.asInt();
                    builder.setCorePoolSize(val);
                    if (mbean != null) mbean.setCorePoolSize(val);
                    break;
                }
                case ThreadPoolExtension.MAX_THREADS: {
                    final int val = resolvedValue.asInt();
                    builder.setMaximumPoolSize(val);
                    if (mbean != null) mbean.setMaximumPoolSize(val);
                    break;
                }
                case ThreadPoolExtension.KEEP_ALIVE: {
                    final Long val = resolvedValue.asLongOrNull();
                    if (val != null) {
                        builder.setKeepAliveTime(val.longValue(), TimeUnit.MILLISECONDS);
                        if (mbean != null) mbean.setKeepAliveTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(val.longValue()));
                    }
                    break;
                }
            }
        }
    }
}
