/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Geronimo" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Geronimo", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * ====================================================================
 */
package org.apache.geronimo.security;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;

import junit.framework.TestCase;
import org.apache.geronimo.security.realm.providers.SQLSecurityRealm;


/**
 *
 * @version $Revision: 1.3 $ $Date: 2004/02/12 08:14:05 $
 */
public class LoginSQLTest extends TestCase {

    private static final String hsqldbURL = "jdbc:hsqldb:target/database/LoginSQLTest";
    SecurityService securityService;

    public void setUp() throws Exception {
        DriverManager.registerDriver(new org.hsqldb.jdbcDriver());

        Connection conn = DriverManager.getConnection(hsqldbURL, "sa", "");


        try {
            conn.prepareStatement("CREATE USER loginmodule PASSWORD password ADMIN;").executeQuery();
        } catch (SQLException e) {
            //ignore, for some reason user already exists.
        }

        conn.prepareStatement("CREATE TABLE Users(UserName VARCHAR(16), Password VARCHAR(16));").executeQuery();
        conn.prepareStatement("CREATE TABLE Groups(GroupName VARCHAR(16), UserName VARCHAR(16));").executeQuery();

        conn.prepareStatement("GRANT SELECT ON Users TO loginmodule;").executeQuery();
        conn.prepareStatement("GRANT SELECT ON Groups TO loginmodule;").executeQuery();

        conn.prepareStatement("INSERT INTO Users VALUES ('izumi', 'violin');").executeQuery();
        conn.prepareStatement("INSERT INTO Users VALUES ('alan', 'starcraft');").executeQuery();
        conn.prepareStatement("INSERT INTO Users VALUES ('george', 'bone');").executeQuery();
        conn.prepareStatement("INSERT INTO Users VALUES ('gracie', 'biscuit');").executeQuery();
        conn.prepareStatement("INSERT INTO Users VALUES ('metro', 'mouse');").executeQuery();

        conn.prepareStatement("INSERT INTO Groups VALUES ('manager', 'izumi');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('it', 'alan');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('pet', 'george');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('pet', 'gracie');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('pet', 'metro');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('dog', 'george');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('dog', 'gracie');").executeQuery();
        conn.prepareStatement("INSERT INTO Groups VALUES ('cat', 'metro');").executeQuery();

        conn.close();

        securityService = new SecurityService();

        SQLSecurityRealm securityRealm = new SQLSecurityRealm("Foo", hsqldbURL, "loginmodule", "password", "SELECT UserName, Password FROM Users", "SELECT GroupName, UserName FROM Groups");
        securityRealm.doStart();
        securityService.setRealms(Collections.singleton(securityRealm));
    }

    public void tearDown() throws Exception {

        Connection conn = DriverManager.getConnection(hsqldbURL, "sa", "");

        try {
            conn.prepareStatement("DROP USER loginmodule;").executeQuery();

            conn.prepareStatement("DROP TABLE Users;").executeQuery();
            conn.prepareStatement("DROP TABLE Groups;").executeQuery();
        } catch (SQLException e) {
            //who knows??
        }

    }

    public void testLogin() throws Exception {

        CallbackHandler handler = new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (int i = 0; i < callbacks.length; i++) {
                    if (callbacks[i] instanceof PasswordCallback) {
                        ((PasswordCallback) callbacks[i]).setPassword("starcraft".toCharArray());
                    } else if (callbacks[i] instanceof NameCallback) {
                        ((NameCallback) callbacks[i]).setName("alan");
                    }
                }
            }

        };
        LoginContext context = new LoginContext("Foo", handler);

        context.login();
        Subject subject = context.getSubject();

        assertTrue("expected non-null subject", subject != null);
        assertTrue("id of subject should be non-null", ContextManager.getSubjectId(subject) != null);
        assertTrue("subject should have two principals", subject.getPrincipals().size() == 2);
        assertTrue("subject should have one realm principal", subject.getPrincipals(RealmPrincipal.class).size() == 1);
        RealmPrincipal principal = (RealmPrincipal)subject.getPrincipals(RealmPrincipal.class).iterator().next();
        assertTrue("id of principal should be non-zero", principal.getId() != 0);

        context.logout();

        assertTrue("id of subject should be null", ContextManager.getSubjectId(subject) == null);
    }
}
