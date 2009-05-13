/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.geronimo.jetty7.security;

import java.util.HashMap;
import java.util.Map;
import java.security.AccessControlContext;

import javax.security.auth.Subject;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.RegistrationListener;
import javax.security.auth.message.config.ServerAuthConfig;

import org.apache.geronimo.gbean.annotation.GBean;
import org.apache.geronimo.gbean.annotation.ParamAttribute;
import org.apache.geronimo.jetty7.handler.JettySecurityHandler;
import org.apache.geronimo.jetty7.security.auth.JAASLoginService;
import org.apache.geronimo.security.jacc.RunAsSource;
import org.apache.geronimo.security.ContextManager;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.jaspi.JaspiAuthenticator;
import org.eclipse.jetty.security.jaspi.ServletCallbackHandler;

/**
 * Fetches a ServerAuthConfig out of the AuthConfigFactory
 * 
 * @version $Rev$ $Date$
 */

@GBean
public class AuthConfigProviderHandlerFactory implements SecurityHandlerFactory {

    private static final String POLICY_CONTEXT_ID_KEY = "javax.security.jacc.PolicyContext";

    private final Map authConfigProperties = new HashMap<Object, Object>();
    private final Subject serviceSubject = null;
    private final String appContext;
    private final boolean allowLazyAuthentication;
//    private final Authenticator authenticator;
    private final LoginService loginService;
    private final ServerAuthConfig serverAuthConfig;
    private final ServletCallbackHandler servletCallbackHandler;
//    private final IdentityService identityService;
//    private final AccessControlContext defaultAcc;


    public AuthConfigProviderHandlerFactory(@ParamAttribute(name = "messageLayer")String messageLayer,
                                            @ParamAttribute(name = "appContext")String appContext,
                                            @ParamAttribute(name = "securityRealm")String securityRealm,
                                            @ParamAttribute(name = "allowLazyAuthentication") boolean allowLazyAuthentication
    ) throws AuthException {
        this.appContext = appContext;
        this.allowLazyAuthentication = allowLazyAuthentication;
        AuthConfigFactory authConfigFactory = AuthConfigFactory.getFactory();
        RegistrationListener listener = new RegistrationListener() {

            public void notify(String layer, String appContext) {
            }
        };
        AuthConfigProvider authConfigProvider = authConfigFactory.getConfigProvider(messageLayer, appContext, listener);
        this.loginService = new JAASLoginService(securityRealm, null);
        servletCallbackHandler = new ServletCallbackHandler(loginService);
        serverAuthConfig = authConfigProvider.getServerAuthConfig(messageLayer, appContext, servletCallbackHandler);
        //TODO appContext is supposed to be server-name<space>context-root

    }

    public JettySecurityHandler buildSecurityHandler(String policyContextID, Subject defaultSubject, RunAsSource runAsSource) {
        if (defaultSubject == null) {
            defaultSubject = ContextManager.EMPTY;
        }
        AccessControlContext defaultAcc = ContextManager.registerSubjectShort(defaultSubject, null, null);
        IdentityService identityService = new JettyIdentityService(defaultAcc, runAsSource);
        authConfigProperties.put(POLICY_CONTEXT_ID_KEY, policyContextID);
        Authenticator authenticator = new JaspiAuthenticator(serverAuthConfig, authConfigProperties, servletCallbackHandler, serviceSubject, allowLazyAuthentication, identityService);
        //login service functionality is already inside the servletCallbackHandler
        return new JettySecurityHandler(policyContextID, authenticator, loginService, identityService, defaultAcc);
    }

}