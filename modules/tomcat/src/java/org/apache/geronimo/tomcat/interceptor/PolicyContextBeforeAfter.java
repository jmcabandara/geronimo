/**
 *
 * Copyright 2003-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.tomcat.interceptor;

import javax.security.auth.Subject;
import javax.security.jacc.PolicyContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.geronimo.security.ContextManager;
import org.apache.geronimo.tomcat.realm.TomcatGeronimoRealm;

public class PolicyContextBeforeAfter implements BeforeAfter{
    
    private final BeforeAfter next;
    private final String policyContextID;
    private final int policyContextIDIndex;

    public PolicyContextBeforeAfter(BeforeAfter next, int policyContextIDIndex, String policyContextID) {
        this.next = next;
        this.policyContextIDIndex = policyContextIDIndex;
        this.policyContextID = policyContextID;
    }

    public void before(Object[] context, ServletRequest httpRequest, ServletResponse httpResponse) {
        
        //Save the old
        PolicyObject policyObject = new PolicyObject();
        policyObject.setContextId(PolicyContext.getContextID());
        policyObject.setSubject(ContextManager.getCurrentCaller());
        //Save the old Request object in case it gets changed 
        //with a x-context Dispatch
        policyObject.setRequest(TomcatGeronimoRealm.getRequest());
        
        context[policyContextIDIndex] = policyObject;
        
        //Set the new
        PolicyContext.setContextID(policyContextID);
        PolicyContext.setHandlerData(httpRequest);
        
        if (next != null) {
            next.before(context, httpRequest, httpResponse);
        }
    }

    public void after(Object[] context, ServletRequest httpRequest, ServletResponse httpResponse) {
        if (next != null) {
            next.after(context, httpRequest, httpResponse);
        }
        
        //Replace the old
        PolicyObject policyObject = (PolicyObject)context[policyContextIDIndex];
        
        PolicyContext.setContextID(policyObject.getContextId());
        ContextManager.setCurrentCaller(policyObject.getSubject());
        TomcatGeronimoRealm.setRequest(policyObject.getRequest());
    }
    
    class PolicyObject{
        private String contextId = null;
        private Subject subject = null;
        private Request request = null;
        
        public String getContextId() {
            return contextId;
        }
        public void setContextId(String contextId) {
            this.contextId = contextId;
        }
        public Subject getSubject() {
            return subject;
        }
        public void setSubject(Subject subject) {
            this.subject = subject;
        }
        public Request getRequest() {
            return request;
        }
        public void setRequest(Request request) {
            this.request = request;
        }
    }

}
