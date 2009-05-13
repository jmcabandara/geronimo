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


package org.apache.geronimo.jetty7.security.auth;

import java.security.Principal;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.geronimo.security.ContextManager;
import org.apache.geronimo.security.realm.providers.PasswordCallbackHandler;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.UserIdentity;

/**
 * @version $Rev$ $Date$
 */
public class JAASLoginService implements LoginService {
    private final String securityRealm;
    private final String realmName;
    private IdentityService identityService;

    public JAASLoginService(String securityRealm, String realmName) {
        this.securityRealm = securityRealm;
        this.realmName = realmName;
    }

    public void logout(UserIdentity userIdentity) {
        //not sure how to do this
    }

    public String getName() {
        return realmName;
    }

    public UserIdentity login(String username, Object credentials) {
        char[] password = (char[]) credentials;
        CallbackHandler callbackHandler = new PasswordCallbackHandler(username, password);
        try {
            LoginContext loginContext = ContextManager.login(securityRealm, callbackHandler);
            Subject establishedSubject = loginContext.getSubject();
            Principal userPrincipal = ContextManager.getCurrentPrincipal(establishedSubject);
            return identityService.newUserIdentity(establishedSubject, userPrincipal, null);
        } catch (LoginException e) {
            return null;
//        } catch (Throwable t) {
//            t.printStackTrace();
//            return null;
        }
    }

    public IdentityService getIdentityService() {
        return identityService;
    }

    public void setIdentityService(IdentityService identityService) {
        this.identityService = identityService;
    }
}
