/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
 
package org.apache.webbeans.newtests.interceptors.beans;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

@RequestScoped
@Named
public class RequestScopedBean
{

    private int i=0;
    private @Inject ApplicationScopedBean myService;

    @PostConstruct
    public void init()
    {
        myService.getJ();
    }

    /** we need this trick, since the injected beans itself are only proxies... */
    public RequestScopedBean getInstance()
    {
        return this;
    }

    public int getI()
    {
        return i;
    }

    public void setI(int i)
    {
        this.i = i;
    }

    public ApplicationScopedBean getMyService()
    {
        return myService;
    }

    public void setMyService(ApplicationScopedBean myService)
    {
        this.myService = myService;
    }


}
