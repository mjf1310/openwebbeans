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
package org.apache.webbeans.test.unittests.event.exception;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;

import javax.enterprise.util.AnnotationLiteral;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.newtests.AbstractUnitTest;
import org.apache.webbeans.test.annotation.binding.Binding1;
import org.apache.webbeans.test.event.LoggedInEvent;
import org.apache.webbeans.test.event.LoggedInObserver;
import org.apache.webbeans.test.event.broke.BrokenEvent;
import org.apache.webbeans.util.ArrayUtil;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class EventExceptionTest extends AbstractUnitTest
{
    @Test
    public void testAddObserverGenericType()
    {
        startContainer();

        try
        {
            Annotation[] anns = new Annotation[1];
            anns[0] = new AnnotationLiteral<Binding1>()
            {
            };

            getBeanManager().fireEvent(new BrokenEvent(), anns);
            Assert.fail("IllegalArgumentException expected");
        }
        catch (IllegalArgumentException e)
        {
            return; // all ok
        }
    }

    @Test
    public void testFireEventGenericType()
    {
        startContainer();
        try
        {
            Annotation[] anns = new Annotation[1];
            anns[0] = new AnnotationLiteral<Binding1>()
            {
            };

            getBeanManager().fireEvent(new BrokenEvent(), anns);
            Assert.fail("IllegalArgumentException expected");
        }
        catch (IllegalArgumentException e)
        {
            return; // all ok
        }
    }

    @Test
    public void testAddObserverDuplicateBinding()
    {
        startContainer();
        try
        {
            Annotation[] anns = new Annotation[2];
            anns[0] = new AnnotationLiteral<Binding1>()
            {
            };
            anns[1] = new AnnotationLiteral<Binding1>()
            {
            };

            LoggedInObserver observer = new LoggedInObserver(ArrayUtil.asSet(anns));
            WebBeansContext.getInstance().getBeanManagerImpl().getNotificationManager().addObserver(observer, LoggedInEvent.class);

            getBeanManager().fireEvent(new LoggedInEvent(), anns);

            Assert.assertEquals("ok", observer.getResult());

            Assert.fail("IllegalArgumentException expected");
        }
        catch (IllegalArgumentException e)
        {
            return; // all ok
        }
    }

    @Test
    public void testAddObserverIllegalArgument()
    {
        startContainer();
        try
        {
            Annotation[] anns = new Annotation[1];
            anns[0] = new AnnotationLiteral<Documented>()
            {
            };
            
            LoggedInObserver observer = new LoggedInObserver(ArrayUtil.asSet(anns));
            WebBeansContext.getInstance().getBeanManagerImpl().getNotificationManager().addObserver(observer, LoggedInEvent.class);
            Assert.fail("IllegalArgumentException expected");
        }
        catch (IllegalArgumentException e)
        {
            return; // all ok
        }
    }
}
