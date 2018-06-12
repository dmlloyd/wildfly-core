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

import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.Resource;

/**
 * A resource implementation that carries an attachment.
 */
final class AttachmentResource<T> extends DelegatingResource {
    private final AttachmentHolder<T> attachmentHolder;

    AttachmentResource(final Resource delegate, final AttachmentHolder<T> attachmentHolder) {
        super(delegate);
        this.attachmentHolder = attachmentHolder;
    }

    AttachmentResource(final ResourceDelegateProvider delegateProvider, final AttachmentHolder<T> attachmentHolder) {
        super(delegateProvider);
        this.attachmentHolder = attachmentHolder;
    }

    public Resource shallowCopy() {
        return new AttachmentResource<T>(getDelegate().shallowCopy(), attachmentHolder);
    }

    public Resource clone() {
        return new AttachmentResource<T>(getDelegate().clone(), attachmentHolder);
    }

    public T getAttachment() {
        return attachmentHolder.get();
    }

    public void setAttachment(T update) {
        attachmentHolder.set(update);
    }

    public T replaceAttachment(T update) {
        return attachmentHolder.getAndSet(update);
    }

    public boolean replaceAttachment(T expect, T update) {
        return attachmentHolder.compareAndSet(expect, update);
    }

    public T removeAttachment() {
        return attachmentHolder.getAndSet(null);
    }

    public boolean removeAttachment(T expect) {
        return attachmentHolder.compareAndSet(expect, null);
    }

    public AttachmentHolder<T> getAttachmentHolder() {
        return attachmentHolder;
    }
}
