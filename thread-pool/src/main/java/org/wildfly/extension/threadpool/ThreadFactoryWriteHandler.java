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

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 */
final class ThreadFactoryWriteHandler extends AbstractWriteAttributeHandler<Void> {
    ThreadFactoryWriteHandler(final SimpleAttributeDefinition threadNameAttr, final SimpleAttributeDefinition stackSizeAttr) {
        super(threadNameAttr, stackSizeAttr);
    }

    protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        applyUpdate(context, attributeName, resolvedValue);
        return true;
    }

    protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final Void handback) throws OperationFailedException {
        applyUpdate(context, attributeName, valueToRestore);
    }

    private void applyUpdate(final OperationContext context, final String attributeName, final ModelNode resolvedValue) {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        @SuppressWarnings("unchecked")
        final AttachmentHolder<ThreadFactoryBuilder> builderHolder = ((AttachmentResource<ThreadFactoryBuilder>) Utils.getDelegate(resource, 2)).getAttachmentHolder();
        final ThreadFactoryBuilder builder = builderHolder.get();

        // this sync is necessary to avoid a race between manual service manipulation and container operations...
        // we can remove it if/when we get proper MSC transactions
        synchronized (builder) {
            switch (attributeName) {
                case ThreadPoolExtension.THREAD_NAME_PATTERN: {
                    builder.setThreadNamePattern(resolvedValue.asString());
                    break;
                }
                case ThreadPoolExtension.STACK_SIZE: {
                    builder.setStackSize(resolvedValue.asLongOrNull());
                    break;
                }
            }
        }
    }
}
