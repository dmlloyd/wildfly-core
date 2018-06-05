/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.modules.ClassTransformer;
import org.jboss.modules.JLIClassTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Marius Bogoevici
 */
public class DelegatingClassFileTransformer implements ClassFileTransformer, ClassTransformer {

    private final List<ClassTransformer> delegateTransformers = new CopyOnWriteArrayList<>();

    public static final AttachmentKey<DelegatingClassFileTransformer> ATTACHMENT_KEY = AttachmentKey.create(DelegatingClassFileTransformer.class);

    private volatile boolean active = false;

    public DelegatingClassFileTransformer() {
    }

    @Deprecated
    public void addTransformer(ClassFileTransformer classFileTransformer) {
        delegateTransformers.add(new JLIClassTransformer(classFileTransformer));
    }

    public void addTransformer(ClassTransformer classFileTransformer) {
        delegateTransformers.add(classFileTransformer);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Deprecated
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] originalBuffer) {
        final ByteBuffer buffer = transform(loader, className, protectionDomain, ByteBuffer.wrap(originalBuffer));
        if (buffer == null) {
            return null;
        } else {
            final int limit = buffer.limit();
            final int position = buffer.position();
            if (buffer.hasArray() && buffer.arrayOffset() == 0 && position == 0 && limit == buffer.capacity()) {
                return buffer.array();
            } else {
                byte[] data = new byte[limit - position];
                buffer.get(data);
                return data;
            }
        }
    }

    public ByteBuffer transform(final ClassLoader loader, final String className, final ProtectionDomain protectionDomain, ByteBuffer classBytes) throws IllegalArgumentException {
        ByteBuffer transformedBuffer = null;
        if (active) {
            for (ClassTransformer transformer : delegateTransformers) {
                ByteBuffer result = transformer.transform(loader, className, protectionDomain, classBytes);
                if (result != null) {
                    classBytes = transformedBuffer = result;
                }
            }
        }
        return transformedBuffer;
    }
}
