/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.jetty6;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.AbstractNameQuery;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.ReferencePatterns;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.jetty6.connector.AJP13Connector;
import org.apache.geronimo.jetty6.connector.HTTPBlockingConnector;
import org.apache.geronimo.jetty6.connector.HTTPSSelectChannelConnector;
import org.apache.geronimo.jetty6.connector.HTTPSSocketConnector;
import org.apache.geronimo.jetty6.connector.HTTPSelectChannelConnector;
import org.apache.geronimo.jetty6.connector.HTTPSocketConnector;
import org.apache.geronimo.jetty6.connector.JettyConnector;
import org.apache.geronimo.jetty6.requestlog.JettyLogManager;
import org.apache.geronimo.kernel.GBeanNotFoundException;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.config.ConfigurationUtil;
import org.apache.geronimo.kernel.config.EditableConfigurationManager;
import org.apache.geronimo.kernel.config.InvalidConfigException;
import org.apache.geronimo.kernel.proxy.ProxyManager;
import org.apache.geronimo.management.geronimo.KeystoreManager;
import org.apache.geronimo.management.geronimo.NetworkConnector;
import org.apache.geronimo.management.geronimo.WebAccessLog;
import org.apache.geronimo.management.geronimo.WebConnector;
import org.apache.geronimo.management.geronimo.WebContainer;
import org.apache.geronimo.management.geronimo.WebManager;

/**
 * Jetty implementation of WebManager.  Knows how to manipulate
 * other Jetty objects for management purposes.
 *
 * @version $Rev:386276 $ $Date$
 */
public class JettyManagerImpl implements WebManager {
    private final static Log log = LogFactory.getLog(JettyManagerImpl.class);

    private static final ConnectorType HTTP_NIO = new ConnectorType("Jetty NIO HTTP Connector");
    private static final ConnectorType HTTPS_NIO = new ConnectorType("Jetty NIO HTTPS Connector");
    private static final ConnectorType HTTP_BLOCKING_NIO = new ConnectorType("Jetty Blocking HTTP Connector using NIO");
    private static final ConnectorType HTTP_BIO = new ConnectorType("Jetty BIO HTTP Connector");
    private static final ConnectorType HTTPS_BIO = new ConnectorType("Jetty BIO HTTPS Connector");
    private static final ConnectorType AJP_NIO = new ConnectorType("Jetty NIO AJP Connector");
    private static List<ConnectorType> CONNECTOR_TYPES = Arrays.asList(
            HTTP_NIO,
            HTTPS_NIO,
            HTTP_BLOCKING_NIO,
            HTTP_BIO,
            HTTPS_BIO,
            AJP_NIO
    );

    private static Map<ConnectorType, List<ConnectorAttribute>> CONNECTOR_ATTRIBUTES = new HashMap<ConnectorType, List<ConnectorAttribute>>();

    //"host", "port", "minThreads", "maxThreads", "bufferSizeBytes", "acceptQueueSize", "lingerMillis", "protocol", "redirectPort", "connectUrl", "maxIdleTimeMs"
    static {
        List<ConnectorAttribute> connectorAttributes = new ArrayList<ConnectorAttribute>();
        connectorAttributes.add(new ConnectorAttribute<String>("host", "0.0.0.0", "The host name or IP to bind to. The normal values are 0.0.0.0 (all interfaces) or localhost (local connections only)", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("port", 8080, "The network port to bind to.", Integer.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("maxThreads", 10, "The maximum number of threads this connector should use to handle incoming requests", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("bufferSizeBytes", 8096, "Buffer size", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("acceptQueueSize", 10, "acceptQueueSize", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("lingerMillis", 30000, "lingerMillis", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("tcpNoDelay", false, "tcpNoDelay", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("redirectPort", 8443, "redirectPort", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Integer>("maxIdleTimeMs", 30000, "maxIdleTimeMs", Integer.class));
        CONNECTOR_ATTRIBUTES.put(HTTP_NIO, connectorAttributes);

        connectorAttributes = new ArrayList<ConnectorAttribute>();
        connectorAttributes.add(new ConnectorAttribute<String>("host", "0.0.0.0", "The host name or IP to bind to. The normal values are 0.0.0.0 (all interfaces) or localhost (local connections only)", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("port", 8443, "The network port to bind to.", Integer.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("maxThreads", 10, "The maximum number of threads this connector should use to handle incoming requests", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("bufferSizeBytes", 8096, "Buffer size", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("acceptQueueSize", 10, "acceptQueueSize", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("lingerMillis", 30000, "lingerMillis", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("tcpNoDelay", false, "tcpNoDelay", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("redirectPort", 8443, "redirectPort", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Integer>("maxIdleTimeMs", 30000, "maxIdleTimeMs", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("clientAuthRequested", false, "clientAuthRequested", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Boolean>("clientAuthRequired", false, "If set, then clients connecting through this connector must supply a valid client certificate.", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<String>("keyStore", "", "The keystore to use for accessing the server's private key", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<String>("trustStore", "", "The keystore containing the trusted certificate entries, including Certification Authority (CA) certificates", String.class));
        //connectorAttributes.add(new ConnectorAttribute<String>("keyAlias", "", "keyAlias", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<String>("secureProtocol", "", "This should normally be set to TLS, though some (IBM) JVMs don't work properly with popular browsers unless it is changed to SSL.", String.class));
        connectorAttributes.add(new ConnectorAttribute<String>("algorithm", "Default", "This should normally be set to match the JVM vendor.", String.class));
        CONNECTOR_ATTRIBUTES.put(HTTPS_NIO, connectorAttributes);

        connectorAttributes = new ArrayList<ConnectorAttribute>();
        connectorAttributes.add(new ConnectorAttribute<String>("host", "0.0.0.0", "The host name or IP to bind to. The normal values are 0.0.0.0 (all interfaces) or localhost (local connections only)", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("port", 8080, "The network port to bind to.", Integer.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("maxThreads", 10, "The maximum number of threads this connector should use to handle incoming requests", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("bufferSizeBytes", 8096, "Buffer size", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("acceptQueueSize", 10, "acceptQueueSize", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("lingerMillis", 30000, "lingerMillis", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("tcpNoDelay", false, "tcpNoDelay", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("redirectPort", 8443, "redirectPort", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Integer>("maxIdleTimeMs", 30000, "maxIdleTimeMs", Integer.class));
        CONNECTOR_ATTRIBUTES.put(HTTP_BIO, connectorAttributes);

        connectorAttributes = new ArrayList<ConnectorAttribute>();
        connectorAttributes.add(new ConnectorAttribute<String>("host", "0.0.0.0", "The host name or IP to bind to. The normal values are 0.0.0.0 (all interfaces) or localhost (local connections only)", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("port", 8443, "The network port to bind to.", Integer.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("maxThreads", 10, "The maximum number of threads this connector should use to handle incoming requests", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("bufferSizeBytes", 8096, "Buffer size", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("acceptQueueSize", 10, "acceptQueueSize", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("lingerMillis", 30000, "lingerMillis", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("tcpNoDelay", false, "tcpNoDelay", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("redirectPort", 8443, "redirectPort", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Integer>("maxIdleTimeMs", 30000, "maxIdleTimeMs", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("clientAuthRequested", false, "clientAuthRequested", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Boolean>("clientAuthRequired", false, "If set, then clients connecting through this connector must supply a valid client certificate.", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<String>("keyStore", "", "The keystore to use for accessing the server's private key", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<String>("trustStore", "", "The keystore containing the trusted certificate entries, including Certification Authority (CA) certificates", String.class));
        //connectorAttributes.add(new ConnectorAttribute<String>("keyAlias", "", "keyAlias", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<String>("secureProtocol", "", "This should normally be set to TLS, though some (IBM) JVMs don't work properly with popular browsers unless it is changed to SSL.", String.class));
        connectorAttributes.add(new ConnectorAttribute<String>("algorithm", "Default", "This should normally be set to match the JVM vendor.", String.class));
        CONNECTOR_ATTRIBUTES.put(HTTPS_BIO, connectorAttributes);

        connectorAttributes = new ArrayList<ConnectorAttribute>();
        connectorAttributes.add(new ConnectorAttribute<String>("host", "0.0.0.0", "The host name or IP to bind to. The normal values are 0.0.0.0 (all interfaces) or localhost (local connections only)", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("port", 8080, "The network port to bind to.", Integer.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("maxThreads", 10, "The maximum number of threads this connector should use to handle incoming requests", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("bufferSizeBytes", 8096, "Buffer size", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("acceptQueueSize", 10, "acceptQueueSize", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("lingerMillis", 30000, "lingerMillis", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("tcpNoDelay", false, "tcpNoDelay", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("redirectPort", 8443, "redirectPort", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Integer>("maxIdleTimeMs", 30000, "maxIdleTimeMs", Integer.class));
        CONNECTOR_ATTRIBUTES.put(HTTP_BLOCKING_NIO, connectorAttributes);

        connectorAttributes = new ArrayList<ConnectorAttribute>();
        connectorAttributes.add(new ConnectorAttribute<String>("host", "0.0.0.0", "The host name or IP to bind to. The normal values are 0.0.0.0 (all interfaces) or localhost (local connections only)", String.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("port", 8009, "The network port to bind to.", Integer.class, true));
        connectorAttributes.add(new ConnectorAttribute<Integer>("maxThreads", 10, "The maximum number of threads this connector should use to handle incoming requests", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("bufferSizeBytes", 8096, "Buffer size", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("acceptQueueSize", 10, "acceptQueueSize", Integer.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("lingerMillis", 30000, "lingerMillis", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Boolean>("tcpNoDelay", false, "tcpNoDelay", Boolean.class));
        connectorAttributes.add(new ConnectorAttribute<Integer>("redirectPort", 8443, "redirectPort", Integer.class));
        //connectorAttributes.add(new ConnectorAttribute<Integer>("maxIdleTimeMs", 30000, "maxIdleTimeMs", Integer.class));
        CONNECTOR_ATTRIBUTES.put(AJP_NIO, connectorAttributes);

    }

    private static Map<ConnectorType, GBeanInfo> CONNECTOR_GBEAN_INFOS = new HashMap<ConnectorType, GBeanInfo>();

    static {
        CONNECTOR_GBEAN_INFOS.put(HTTP_NIO, HTTPSelectChannelConnector.GBEAN_INFO);
        CONNECTOR_GBEAN_INFOS.put(HTTPS_NIO, HTTPSSelectChannelConnector.GBEAN_INFO);
        CONNECTOR_GBEAN_INFOS.put(HTTP_BLOCKING_NIO, HTTPBlockingConnector.GBEAN_INFO);
        CONNECTOR_GBEAN_INFOS.put(HTTP_BIO, HTTPSocketConnector.GBEAN_INFO);
        CONNECTOR_GBEAN_INFOS.put(HTTPS_BIO, HTTPSSocketConnector.GBEAN_INFO);
        CONNECTOR_GBEAN_INFOS.put(AJP_NIO, AJP13Connector.GBEAN_INFO);
    }

    private final Kernel kernel;

    public JettyManagerImpl(Kernel kernel) {
        this.kernel = kernel;
    }

    public String getProductName() {
        return "Jetty";
    }

    /**
     * Creates a new connector, and returns the ObjectName for it.  Note that
     * the connector may well require further customization before being fully
     * functional (e.g. SSL settings for an HTTPS connector).
     */
    public WebConnector addConnector(WebContainer container, String uniqueName, String protocol, String host, int port) {
        AbstractName containerName = kernel.getAbstractNameFor(container);
        AbstractName name = kernel.getNaming().createSiblingName(containerName, uniqueName, NameFactory.GERONIMO_SERVICE);
        GBeanData connector;
        if (protocol.equals(PROTOCOL_HTTP)) {
            connector = new GBeanData(name, HTTPSocketConnector.GBEAN_INFO);
        } else if (protocol.equals(PROTOCOL_HTTPS)) {
            connector = new GBeanData(name, HTTPSSocketConnector.GBEAN_INFO);
            AbstractNameQuery query = new AbstractNameQuery(KeystoreManager.class.getName());
            connector.setReferencePattern("KeystoreManager", query);
            //todo: default HTTPS settings
        } else if (protocol.equals(PROTOCOL_AJP)) {
            connector = new GBeanData(name, AJP13Connector.GBEAN_INFO);
        } else {
            throw new IllegalArgumentException("Invalid protocol '" + protocol + "'");
        }
        connector.setAttribute("host", host);
        connector.setAttribute("port", new Integer(port));
        //connector.setAttribute("minThreads", new Integer(10));        
        connector.setAttribute("protocol", protocol);
        connector.setAttribute("maxThreads", new Integer(50));
        connector.setReferencePattern(JettyConnector.CONNECTOR_CONTAINER_REFERENCE, containerName);
        EditableConfigurationManager mgr = ConfigurationUtil.getEditableConfigurationManager(kernel);
        if (mgr != null) {
            try {
                mgr.addGBeanToConfiguration(containerName.getArtifact(), connector, false);
                return (WebConnector) kernel.getProxyManager().createProxy(name, JettyWebConnector.class.getClassLoader());
            } catch (InvalidConfigException e) {
                log.error("Unable to add GBean", e);
                return null;
            } finally {
                ConfigurationUtil.releaseConfigurationManager(kernel, mgr);
            }
        } else {
            log.warn("The ConfigurationManager in the kernel does not allow editing");
            return null;
        }
    }

    /**
     * Get a list of containers for this web implementation.
     */
    public Object[] getContainers() {
        ProxyManager proxyManager = kernel.getProxyManager();
        AbstractNameQuery query = new AbstractNameQuery(JettyContainer.class.getName());
        Set names = kernel.listGBeans(query);
        JettyContainer[] results = new JettyContainer[names.size()];
        int i = 0;
        for (Iterator it = names.iterator(); it.hasNext(); i++) {
            AbstractName name = (AbstractName) it.next();
            results[i] = (JettyContainer) proxyManager.createProxy(name, JettyContainer.class.getClassLoader());
        }
        return results;
    }

    /**
     * Gets the protocols that this web container supports (that you can create
     * connectors for).
     */
    public String[] getSupportedProtocols() {
        return new String[]{PROTOCOL_HTTP, PROTOCOL_HTTPS, PROTOCOL_AJP};
    }

    /**
     * Removes a connector.  This shuts it down if necessary, and removes it
     * from the server environment.  It must be a connector that this container
     * is responsible for.
     *
     * @param connectorName
     */
    public void removeConnector(AbstractName connectorName) {
        try {
            GBeanInfo info = kernel.getGBeanInfo(connectorName);
            boolean found = false;
            Set intfs = info.getInterfaces();
            for (Iterator it = intfs.iterator(); it.hasNext();) {
                String intf = (String) it.next();
                if (intf.equals(JettyWebConnector.class.getName())) {
                    found = true;
                }
            }
            if (!found) {
                throw new GBeanNotFoundException(connectorName);
            }
            EditableConfigurationManager mgr = ConfigurationUtil.getEditableConfigurationManager(kernel);
            if (mgr != null) {
                try {
                    mgr.removeGBeanFromConfiguration(connectorName.getArtifact(), connectorName);
                } catch (InvalidConfigException e) {
                    log.error("Unable to add GBean", e);
                } finally {
                    ConfigurationUtil.releaseConfigurationManager(kernel, mgr);
                }
            } else {
                log.warn("The ConfigurationManager in the kernel does not allow editing");
            }
        } catch (GBeanNotFoundException e) {
            log.warn("No such GBean '" + connectorName + "'"); //todo: what if we want to remove a failed GBean?
        } catch (Exception e) {
            log.error(e);
        }
    }

    /**
     * Gets the ObjectNames of any existing connectors for the specified
     * protocol.
     *
     * @param protocol A protocol as returned by getSupportedProtocols
     */
    public NetworkConnector[] getConnectors(String protocol) {
        if (protocol == null) {
            return getConnectors();
        }
        List result = new ArrayList();
        ProxyManager proxyManager = kernel.getProxyManager();
        AbstractNameQuery query = new AbstractNameQuery(JettyWebConnector.class.getName());
        Set names = kernel.listGBeans(query);
        for (Iterator it = names.iterator(); it.hasNext();) {
            AbstractName name = (AbstractName) it.next();
            try {
                if (kernel.getAttribute(name, "protocol").equals(protocol)) {
                    result.add(proxyManager.createProxy(name, JettyWebConnector.class.getClassLoader()));
                }
            } catch (Exception e) {
                log.error("Unable to check the protocol for a connector", e);
            }
        }
        return (JettyWebConnector[]) result.toArray(new JettyWebConnector[names.size()]);
    }

    public WebAccessLog getAccessLog(WebContainer container) {
        AbstractNameQuery query = new AbstractNameQuery(JettyLogManager.class.getName());
        Set names = kernel.listGBeans(query);
        if (names.size() == 0) {
            return null;
        } else if (names.size() > 1) {
            throw new IllegalStateException("Should not be more than one Jetty access log manager");
        }
        return (WebAccessLog) kernel.getProxyManager().createProxy((AbstractName) names.iterator().next(), JettyLogManager.class.getClassLoader());
    }

    public List<ConnectorType> getConnectorTypes() {
        return CONNECTOR_TYPES;
    }

    public List<ConnectorAttribute> getConnectorAttributes(ConnectorType connectorType) {
        return ConnectorAttribute.copy(CONNECTOR_ATTRIBUTES.get(connectorType));
    }

    public AbstractName getConnectorConfiguration(ConnectorType connectorType, List<ConnectorAttribute> connectorAttributes, WebContainer container, String uniqueName) {
        GBeanInfo gbeanInfo = CONNECTOR_GBEAN_INFOS.get(connectorType);
        AbstractName containerName = kernel.getAbstractNameFor(container);
        AbstractName name = kernel.getNaming().createSiblingName(containerName, uniqueName, NameFactory.GERONIMO_SERVICE);
        GBeanData gbeanData = new GBeanData(name, gbeanInfo);
        gbeanData.setReferencePattern(JettyConnector.CONNECTOR_CONTAINER_REFERENCE, containerName);
        for (ConnectorAttribute connectorAttribute : connectorAttributes) {
            Object value = connectorAttribute.getValue();
            if (value != null) {
                gbeanData.setAttribute(connectorAttribute.getAttributeName(), connectorAttribute.getValue());
            }
        }
        
        // provide a reference to KeystoreManager gbean for HTTPS connectors
        if (connectorType.equals(HTTPS_NIO) || connectorType.equals(HTTPS_BIO)) {
            AbstractNameQuery query = new AbstractNameQuery(KeystoreManager.class.getName());
            gbeanData.setReferencePattern("KeystoreManager", query);
        }
        
        EditableConfigurationManager mgr = ConfigurationUtil.getEditableConfigurationManager(kernel);
        if (mgr != null) {
            try {
                mgr.addGBeanToConfiguration(containerName.getArtifact(), gbeanData, false);
            } catch (InvalidConfigException e) {
                log.error("Unable to add GBean", e);
                return null;
            } finally {
                ConfigurationUtil.releaseConfigurationManager(kernel, mgr);
            }
        } else {
            log.warn("The ConfigurationManager in the kernel does not allow editing");
            return null;
        }
        return name;
    }
    
    public ConnectorType getConnectorType(AbstractName connectorName) {
        ConnectorType connectorType = null; 
        try {
            GBeanInfo info = kernel.getGBeanInfo(connectorName);
            boolean found = false;
            Set intfs = info.getInterfaces();
            for (Iterator it = intfs.iterator(); it.hasNext() && !found;) {
                String intf = (String) it.next();
                if (intf.equals(JettyWebConnector.class.getName())) {
                    found = true;
                }
            }
            if (!found) {
                throw new GBeanNotFoundException(connectorName);
            }
            String searchingFor = info.getName();
            for (Entry<ConnectorType, GBeanInfo> entry : CONNECTOR_GBEAN_INFOS.entrySet() ) {
                String candidate = entry.getValue().getName();
                if (candidate.equals(searchingFor)) {
                    return entry.getKey();
                }
            }
        } catch (GBeanNotFoundException e) {
            log.warn("No such GBean '" + connectorName + "'");
        } catch (Exception e) {
            log.error(e);
        }
            
        return connectorType;
    }

    /**
     * Gets the ObjectNames of any existing connectors.
     */
    public NetworkConnector[] getConnectors() {
        ProxyManager proxyManager = kernel.getProxyManager();
        AbstractNameQuery query = new AbstractNameQuery(JettyWebConnector.class.getName());
        Set names = kernel.listGBeans(query);
        JettyWebConnector[] results = new JettyWebConnector[names.size()];
        int i = 0;
        for (Iterator it = names.iterator(); it.hasNext(); i++) {
            AbstractName name = (AbstractName) it.next();
            results[i] = (JettyWebConnector) proxyManager.createProxy(name, JettyWebConnector.class.getClassLoader());
        }
        return results;
    }

    public NetworkConnector[] getConnectorsForContainer(Object container, String protocol) {
        if (protocol == null) {
            return getConnectorsForContainer(container);
        }
        AbstractName containerName = kernel.getAbstractNameFor(container);
        ProxyManager mgr = kernel.getProxyManager();
        try {
            List results = new ArrayList();
            AbstractNameQuery query = new AbstractNameQuery(JettyWebConnector.class.getName());
            Set set = kernel.listGBeans(query); // all Jetty connectors
            for (Iterator it = set.iterator(); it.hasNext();) {
                AbstractName name = (AbstractName) it.next(); // a single Jetty connector
                GBeanData data = kernel.getGBeanData(name);
                ReferencePatterns refs = data.getReferencePatterns(JettyConnector.CONNECTOR_CONTAINER_REFERENCE);
                if (containerName.equals(refs.getAbstractName())) {
                    try {
                        String testProtocol = (String) kernel.getAttribute(name, "protocol");
                        if (testProtocol != null && testProtocol.equals(protocol)) {
                            results.add(mgr.createProxy(name, JettyWebConnector.class.getClassLoader()));
                        }
                    } catch (Exception e) {
                        log.error("Unable to look up protocol for connector '" + name + "'", e);
                    }
                    break;
                }
            }
            return (JettyWebConnector[]) results.toArray(new JettyWebConnector[results.size()]);
        } catch (Exception e) {
            throw (IllegalArgumentException) new IllegalArgumentException("Unable to look up connectors for Jetty container '" + containerName + "': ").initCause(e);
        }
    }

    public NetworkConnector[] getConnectorsForContainer(Object container) {
        AbstractName containerName = kernel.getAbstractNameFor(container);
        ProxyManager mgr = kernel.getProxyManager();
        try {
            List results = new ArrayList();
            AbstractNameQuery query = new AbstractNameQuery(JettyWebConnector.class.getName());
            Set set = kernel.listGBeans(query); // all Jetty connectors
            for (Iterator it = set.iterator(); it.hasNext();) {
                AbstractName name = (AbstractName) it.next(); // a single Jetty connector
                GBeanData data = kernel.getGBeanData(name);
                ReferencePatterns refs = data.getReferencePatterns(JettyConnector.CONNECTOR_CONTAINER_REFERENCE);
                if (containerName.equals(refs.getAbstractName())) {
                    results.add(mgr.createProxy(name, JettyWebConnector.class.getClassLoader()));
                }
            }
            return (JettyWebConnector[]) results.toArray(new JettyWebConnector[results.size()]);
        } catch (Exception e) {
            throw (IllegalArgumentException) new IllegalArgumentException("Unable to look up connectors for Jetty container '" + containerName + "'").initCause(e);
        }
    }

    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoFactory = GBeanInfoBuilder.createStatic("Jetty Web Manager", JettyManagerImpl.class);
        infoFactory.addAttribute("kernel", Kernel.class, false);
        infoFactory.addInterface(WebManager.class);
        infoFactory.setConstructor(new String[]{"kernel"});
        GBEAN_INFO = infoFactory.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }
}
