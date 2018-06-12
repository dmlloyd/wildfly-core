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

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 */
final class GeneralRemoveHandler extends AbstractRemoveStepHandler {
    private final ServiceName baseServiceName;
    private final GeneralAddHandler addOperation;

    GeneralRemoveHandler(ServiceName baseServiceName, final GeneralAddHandler addOperation) {
        this.baseServiceName = baseServiceName;
        this.addOperation = addOperation;
    }

    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) {
        final PathAddress address = context.getCurrentAddress();
        final String name = address.getLastElement().getValue();

        context.removeService(baseServiceName.append(name));
    }

    protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        addOperation.performRuntime(context, operation, context.readResource(PathAddress.EMPTY_ADDRESS));
    }
}
