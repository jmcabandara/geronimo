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
package org.apache.geronimo.console.util;

import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.geronimo.kernel.KernelRegistry;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.proxy.GeronimoManagedBean;
import org.apache.geronimo.management.J2EEDomain;
import org.apache.geronimo.management.geronimo.JVM;
import org.apache.geronimo.management.geronimo.J2EEServer;
import org.apache.geronimo.management.geronimo.WebContainer;
import org.apache.geronimo.management.geronimo.WebConnector;
import org.apache.geronimo.management.geronimo.EJBContainer;
import org.apache.geronimo.management.geronimo.EJBConnector;
import org.apache.geronimo.management.geronimo.JMSManager;
import org.apache.geronimo.management.geronimo.JMSBroker;
import org.apache.geronimo.management.geronimo.JMSConnector;
import org.apache.geronimo.system.logging.SystemLog;
import org.apache.geronimo.pool.GeronimoExecutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Rev: 46019 $ $Date: 2004-09-14 05:56:06 -0400 (Tue, 14 Sep 2004) $
 */
public class PortletManager {
    private final static Log log = LogFactory.getLog(PortletManager.class);
    // The following are currently static due to having only one server/JVM/etc. per Geronimo
    private final static String HELPER_KEY = "org.apache.geronimo.console.ManagementHelper";
    private final static String DOMAIN_KEY = "org.apache.geronimo.console.J2EEDomain";
    private final static String SERVER_KEY = "org.apache.geronimo.console.J2EEServer";
    private final static String JVM_KEY = "org.apache.geronimo.console.JVM";
    private final static String WEB_CONTAINER_KEY = "org.apache.geronimo.console.WebContainer";
    private final static String JMS_MANAGER_KEY = "org.apache.geronimo.console.JMSManager";
    private final static String EJB_CONTAINER_KEY = "org.apache.geronimo.console.EJBContainer";
    private final static String SYSTEM_LOG_KEY = "org.apache.geronimo.console.SystemLog";
    // The following may change based on the user's selections
        // nothing yet

    private static ManagementHelper createHelper(PortletRequest request) {
        //todo: consider making this configurable; we could easily connect to a remote kernel if we wanted to
        Kernel kernel = null;
        try {
            kernel = (Kernel) new InitialContext().lookup("java:comp/GeronimoKernel");
        } catch (NamingException e) {
//            log.error("Unable to look up kernel in JNDI", e);
        }
        if(kernel == null) {
            log.debug("Unable to find kernel in JNDI; using KernelRegistry instead");
            kernel = KernelRegistry.getSingleKernel();
        }
        return new KernelManagementHelper(kernel);
    }

    public static ManagementHelper getManagementHelper(PortletRequest request) {
        ManagementHelper helper = (ManagementHelper) request.getPortletSession(true).getAttribute(HELPER_KEY, PortletSession.APPLICATION_SCOPE);
        if(helper == null) {
            helper = createHelper(request);
            request.getPortletSession().setAttribute(HELPER_KEY, helper, PortletSession.APPLICATION_SCOPE);
        }
        return helper;
    }

    public static J2EEDomain getCurrentDomain(PortletRequest request) {
        J2EEDomain domain = (J2EEDomain) request.getPortletSession(true).getAttribute(DOMAIN_KEY, PortletSession.APPLICATION_SCOPE);
        if(domain == null) {
            domain = getManagementHelper(request).getDomains()[0]; //todo: some day, select a domain
            request.getPortletSession().setAttribute(DOMAIN_KEY, domain, PortletSession.APPLICATION_SCOPE);
        }
        return domain;

    }

    public static J2EEServer getCurrentServer(PortletRequest request) {
        J2EEServer server = (J2EEServer) request.getPortletSession(true).getAttribute(SERVER_KEY, PortletSession.APPLICATION_SCOPE);
        if(server == null) {
            ManagementHelper helper = getManagementHelper(request);
            server = helper.getServers(getCurrentDomain(request))[0]; //todo: some day, select a server from the domain
            request.getPortletSession().setAttribute(SERVER_KEY, server, PortletSession.APPLICATION_SCOPE);
        }
        return server;
    }

    public static JVM getCurrentJVM(PortletRequest request) {
        JVM jvm = (JVM) request.getPortletSession(true).getAttribute(JVM_KEY, PortletSession.APPLICATION_SCOPE);
        if(jvm == null) {
            ManagementHelper helper = getManagementHelper(request);
            jvm = helper.getJavaVMs(getCurrentServer(request))[0]; //todo: some day, select a JVM from the server
            request.getPortletSession().setAttribute(JVM_KEY, jvm, PortletSession.APPLICATION_SCOPE);
        }
        return jvm;
    }

    public static WebContainer getCurrentWebContainer(PortletRequest request) {
        WebContainer container = (WebContainer) request.getPortletSession(true).getAttribute(WEB_CONTAINER_KEY, PortletSession.APPLICATION_SCOPE);
        if(container == null) {
            ManagementHelper helper = getManagementHelper(request);
            container = helper.getWebContainer(getCurrentServer(request));
            request.getPortletSession().setAttribute(WEB_CONTAINER_KEY, container, PortletSession.APPLICATION_SCOPE);
        }
        return container;
    }

    public static WebConnector createWebConnector(PortletRequest request, String name, String protocol, String host, int port) {
        WebContainer container = getCurrentWebContainer(request);
        String objectName = container.addConnector(name, protocol, host, port);
        ManagementHelper helper = getManagementHelper(request);
        return (WebConnector)helper.getObject(objectName);
    }

    public static WebConnector[] getWebConnectors(PortletRequest request) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getWebConnectors(getCurrentWebContainer(request));
    }

    public static WebConnector[] getWebConnectors(PortletRequest request, String protocol) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getWebConnectors(getCurrentWebContainer(request),protocol);
    }

    public static EJBContainer getCurrentEJBContainer(PortletRequest request) {
        EJBContainer container = (EJBContainer) request.getPortletSession(true).getAttribute(EJB_CONTAINER_KEY, PortletSession.APPLICATION_SCOPE);
        if(container == null) {
            ManagementHelper helper = getManagementHelper(request);
            container = helper.getEJBContainer(getCurrentServer(request));
            request.getPortletSession().setAttribute(EJB_CONTAINER_KEY, container, PortletSession.APPLICATION_SCOPE);
        }
        return container;
    }

    public static EJBConnector createEJBConnector(PortletRequest request, String name, String protocol, String threadPoolObjectName, String host, int port) {
        EJBContainer container = getCurrentEJBContainer(request);
        String objectName = container.addConnector(name, protocol, threadPoolObjectName, host, port);
        ManagementHelper helper = getManagementHelper(request);
        return (EJBConnector)helper.getObject(objectName);
    }

    public static EJBConnector[] getEJBConnectors(PortletRequest request) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getEJBConnectors(getCurrentEJBContainer(request));
    }

    public static EJBConnector[] getEJBConnectors(PortletRequest request, String protocol) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getEJBConnectors(getCurrentEJBContainer(request), protocol);
    }

    public static JMSManager getCurrentJMSManager(PortletRequest request) {
        JMSManager manager = (JMSManager) request.getPortletSession(true).getAttribute(JMS_MANAGER_KEY, PortletSession.APPLICATION_SCOPE);
        if(manager == null) {
            ManagementHelper helper = getManagementHelper(request);
            manager = helper.getJMSManager(getCurrentServer(request));
            request.getPortletSession().setAttribute(JMS_MANAGER_KEY, manager, PortletSession.APPLICATION_SCOPE);
        }
        return manager;
    }

    public static JMSBroker[] getJMSBrokers(PortletRequest request) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getJMSBrokers(getCurrentJMSManager(request));
    }

    public static JMSConnector createJMSConnector(PortletRequest request, JMSBroker broker, String name, String protocol, String host, int port) {
        JMSManager manager = getCurrentJMSManager(request);
        String objectName = manager.addConnector(((GeronimoManagedBean)broker).getObjectName(), name, protocol, host, port);
        ManagementHelper helper = getManagementHelper(request);
        return (JMSConnector)helper.getObject(objectName);
    }

    public static JMSConnector[] getJMSConnectors(PortletRequest request) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getJMSConnectors(getCurrentJMSManager(request));
    }

    public static JMSConnector[] getJMSConnectors(PortletRequest request, String protocol) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getJMSConnectors(getCurrentJMSManager(request), protocol);
    }

    public static JMSConnector[] getBrokerJMSConnectors(PortletRequest request, JMSBroker broker) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getJMSConnectors(getCurrentJMSManager(request), broker);
    }

    public static JMSConnector[] getBrokerJMSConnectors(PortletRequest request, JMSBroker broker, String protocol) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getJMSConnectors(getCurrentJMSManager(request), broker, protocol);
    }

    public static GeronimoExecutor[] getThreadPools(PortletRequest request) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getThreadPools(getCurrentServer(request));
    }

    public static String getGBeanDescription(PortletRequest request, String objectName) {
        ManagementHelper helper = getManagementHelper(request);
        return helper.getGBeanDescription(objectName);
    }

    public static SystemLog getCurrentSystemLog(PortletRequest request) {
        SystemLog log = (SystemLog) request.getPortletSession(true).getAttribute(SYSTEM_LOG_KEY, PortletSession.APPLICATION_SCOPE);
        if(log == null) {
            ManagementHelper helper = getManagementHelper(request);
            log = helper.getSystemLog(getCurrentJVM(request));
            request.getPortletSession().setAttribute(SYSTEM_LOG_KEY, log, PortletSession.APPLICATION_SCOPE);
        }
        return log;
    }

    public static GeronimoManagedBean getManagedBean(PortletRequest request, String name) {
        ManagementHelper helper = getManagementHelper(request);
        return (GeronimoManagedBean) helper.getObject(name);
    }
}
