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
package org.apache.geronimo.jetty.deployment;

import java.util.Arrays;
import javax.enterprise.deploy.model.DDBeanRoot;
import javax.enterprise.deploy.model.DDBean;
import javax.enterprise.deploy.spi.DeploymentConfiguration;
import javax.enterprise.deploy.spi.DConfigBean;

import org.apache.geronimo.deployment.tools.loader.WebDeployable;
import org.apache.geronimo.deployment.plugin.j2ee.URIRefConfigBean;

/**
 * 
 * 
 * @version $Revision: 1.2 $ $Date: 2004/01/25 01:08:25 $
 */
public class WebAppDConfigTest extends DeployerTestCase {
    private DeploymentConfiguration config;
    private WebDeployable deployable;

    public void testWebAppRoot() throws Exception {
        DDBeanRoot ddBeanRoot = deployable.getDDBeanRoot();
        WebAppDConfigRoot configRoot = (WebAppDConfigRoot) config.getDConfigBeanRoot(ddBeanRoot);
        assertNotNull(configRoot);
        assertTrue(Arrays.equals(new String[]{"web-app"}, configRoot.getXpaths()));
        assertNotNull(configRoot.getDConfigBean(ddBeanRoot.getChildBean("web-app")[0]));
        assertNull(configRoot.getDConfigBean(ddBeanRoot.getChildBean("web-app/description")[0]));
    }

    public void testWebApp() throws Exception {
        DDBeanRoot ddBeanRoot = deployable.getDDBeanRoot();
        WebAppDConfigRoot configRoot = (WebAppDConfigRoot) config.getDConfigBeanRoot(ddBeanRoot);
        WebAppDConfigBean webApp = (WebAppDConfigBean) configRoot.getDConfigBean(ddBeanRoot.getChildBean("web-app")[0]);
        assertNotNull(webApp);
    }

    public void testEncRef() throws Exception {
        DDBeanRoot ddBeanRoot = deployable.getDDBeanRoot();
        WebAppDConfigRoot configRoot = (WebAppDConfigRoot) config.getDConfigBeanRoot(ddBeanRoot);

        DDBean[] ddBeans;
        DConfigBean dcBean;

        ddBeans = ddBeanRoot.getChildBean("web-app/ejb-ref/ejb-ref-name");
        assertNotNull(ddBeans);
        assertEquals(1, ddBeans.length);
        assertEquals("fake-ejb-ref", ddBeans[0].getText());
        dcBean = configRoot.getDConfigBean(ddBeans[0]);
//        assertNotNull(dcBean);
//        assertTrue(dcBean instanceof URIRefConfigBean);
//        ((URIRefConfigBean)dcBean).setTargetURI("blah-ejb-ref");
//        dcBean = configRoot.getDConfigBean(ddBeans[0]);
//        assertNotNull(dcBean);
//        assertTrue(dcBean instanceof URIRefConfigBean);
//        assertEquals("blah-ejb-ref", ((URIRefConfigBean)dcBean).getTargetURI());
//
//        ddBeans = ddBeanRoot.getChildBean("web-app/ejb-local-ref/ejb-ref-name");
//        assertNotNull(ddBeans);
//        assertEquals(1, ddBeans.length);
//        assertEquals("fake-ejb-local-ref", ddBeans[0].getText());
//        dcBean = configRoot.getDConfigBean(ddBeans[0]);
//        assertNotNull(dcBean);
//        assertTrue(dcBean instanceof URIRefConfigBean);
//        ((URIRefConfigBean)dcBean).setTargetURI("blah-ejb-local-ref");
//        dcBean = configRoot.getDConfigBean(ddBeans[0]);
//        assertNotNull(dcBean);
//        assertTrue(dcBean instanceof URIRefConfigBean);
//        assertEquals("blah-ejb-local-ref", ((URIRefConfigBean)dcBean).getTargetURI());
    }

    protected void setUp() throws Exception {
        super.setUp();
        deployable = new WebDeployable(classLoader.getResource("deployables/war1/"));
        config = manager.createConfiguration(deployable);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
}
