/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.portable.events.generics;

import javax.enterprise.inject.spi.AnnotatedType;

import org.apache.webbeans.portable.events.ProcessAnnotatedTypeImpl;

@SuppressWarnings("unchecked")
public class GProcessAnnotatedType extends ProcessAnnotatedTypeImpl implements GenericBeanEvent
{
    private boolean after = false;

    public GProcessAnnotatedType(AnnotatedType annotatedType )
    {
        super(annotatedType);
    }

    public void setAfter()
    {
        this.after = true;
    }

    @Override
    public AnnotatedType getAnnotatedType()
    {
        if (after)
        {
            throw new IllegalStateException("Can't call ProcessAnnotatedType.getAnnotatedType() after the event");
        }
        return super.getAnnotatedType();
    }

    @Override
    public void setAnnotatedType(AnnotatedType type)
    {
        if (after)
        {
            throw new IllegalStateException("Can't call ProcessAnnotatedType.setAnnotatedType(at) after the event");
        }
        super.setAnnotatedType(type);
    }

    @Override
    public void veto()
    {
        if (after)
        {
            throw new IllegalStateException("Can't call ProcessAnnotatedType.veto() after the event");
        }
        super.veto();
    }

    @Override
    public Class<?> getBeanClassFor(Class<?> eventClass)
    {
        return getAnnotatedType().getJavaClass();
    }
}
