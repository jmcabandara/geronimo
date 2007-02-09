/**
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
package org.apache.geronimo.yoko;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.gbean.GBeanLifecycle;
import org.apache.geronimo.security.deploy.DefaultDomainPrincipal;
import org.apache.geronimo.security.deploy.DefaultPrincipal;
import org.apache.geronimo.security.deploy.DefaultRealmPrincipal;
import org.apache.geronimo.corba.CORBABean;
import org.apache.geronimo.corba.CSSBean;
import org.apache.geronimo.corba.NameService;
import org.apache.geronimo.corba.ORBConfiguration;
import org.apache.geronimo.corba.security.config.ConfigAdapter;
import org.apache.geronimo.corba.security.config.ConfigException;
import org.apache.geronimo.corba.security.config.tss.TSSConfig;
import org.apache.geronimo.corba.security.config.tss.TSSSSLTransportConfig;
import org.apache.geronimo.corba.security.config.tss.TSSTransportMechConfig;
import org.apache.yoko.orb.CosNaming.tnaming.TransientNameService;
import org.apache.yoko.orb.CosNaming.tnaming.TransientServiceException;
import org.apache.yoko.orb.OB.ZERO_PORT_POLICY_ID; 
import org.omg.CORBA.Any;
import org.omg.CORBA.ORB;
import org.omg.CORBA.Policy;


/**
 * A ConfigAdapter instance for the Apache Yoko
 * CORBA support.
 * @version $Revision: 497125 $ $Date: 2007-01-17 10:51:30 -0800 (Wed, 17 Jan 2007) $
 */
public class ORBConfigAdapter implements GBeanLifecycle, ConfigAdapter {

    private final Log log = LogFactory.getLog(ORBConfigAdapter.class);

    // static registry used to hook up bean instances with
    private static final HashMap registry = new HashMap();

    public ORBConfigAdapter() {
    }

    /**
     * Start the config adapter GBean.  This is basically
     * an opportunity to set any system properties
     * required to make the ORB hook ups.  In particular,
     * this makes the ORB hookups for the RMI over IIOP
     * support.
     *
     * @exception Exception
     */
    public void doStart() throws Exception {
        // define the default ORB for ORB.init();
        System.setProperty("org.omg.CORBA.ORBClass", "org.apache.yoko.orb.CORBA.ORB");
        System.setProperty("org.omg.CORBA.ORBSingletonClass", "org.apache.yoko.orb.CORBA.ORBSingleton");

        // redirect the RMI implementation to use the Yoko ORB.
        System.setProperty("javax.rmi.CORBA.PortableRemoteObjectClass", "org.apache.yoko.rmi.impl.PortableRemoteObjectImpl");
        System.setProperty("javax.rmi.CORBA.StubClass", "org.apache.yoko.rmi.impl.StubImpl");
        // this hooks the util class and allows us to override certain functions
        System.setProperty("javax.rmi.CORBA.UtilClass", "org.apache.geronimo.corba.util.UtilDelegateImpl");
        // this tells the openejb UtilDelegateImpl which implementation to delegate non-overridden
        // operations to.
        System.setProperty("org.apache.geronimo.corba.UtilDelegateClass", "org.apache.yoko.rmi.impl.UtilImpl");
        // this allows us to hook RMI stub invocation/serialization events. 
        System.setProperty("org.apache.yoko.rmi.RMIStubInitializerClass", "org.apache.geronimo.yoko.RMIStubHandlerFactory");

        // ok, now we have a potential classloading problem because of where our util delegates are located.
        // by forcing these classes to load now using our class loader, we can ensure things are properly initialized
        this.getClass().getClassLoader().loadClass("javax.rmi.PortableRemoteObject");

        log.debug("Started  Yoko ORBConfigAdapter");
    }

    public void doStop() throws Exception {
        // nothing really required here.
        log.debug("Stopped Yoko ORBConfigAdapter");
    }

    public void doFail() {
        // nothing much to do.
        log.warn("Failed Yoko ORBConfigAdapter");
    }

    /**
     * Create an ORB for a CORBABean server context.
     *
     * @param server The CORBABean that owns this ORB's configuration.
     *
     * @return An ORB instance configured for the CORBABean.
     * @exception ConfigException
     */
    public ORB createServerORB(CORBABean server)  throws ConfigException {
        ORB orb = createORB(server.getURI(), (ORBConfiguration)server, translateToArgs(server), translateToProps(server));

        // check the tss config for a transport mech definition.  If we have one, then 
        // the port information will be passed in that config, and the port in the IIOP profile 
        // needs to be zero. 
        TSSConfig config = server.getTssConfig();
        TSSTransportMechConfig transportMech = config.getTransport_mech();
        if (transportMech != null) {
            if (transportMech instanceof TSSSSLTransportConfig) {
                Any any = orb.create_any();
                any.insert_boolean(true);

                try {
                    Policy portPolicy = orb.create_policy(ZERO_PORT_POLICY_ID.value, any);
                    Policy[] overrides = new Policy [] { portPolicy };
                    server.setPolicyOverrides(overrides);
                } catch (org.omg.CORBA.PolicyError e) {
                    // shouldn't happen, but we'll let things continue with no policy set. 
                }

            }
        }

        return orb;
    }

    /**
     * Create an ORB for a CSSBean client context.
     *
     * @param client The configured CSSBean used for access.
     *
     * @return An ORB instance configured for this client access.
     * @exception ConfigException
     */
    public ORB createClientORB(CSSBean client)  throws ConfigException {
        return createORB(client.getURI(), (ORBConfiguration)client, translateToArgs(client), translateToProps(client));
    }

    /**
     * Create an ORB for a CSSBean name service client context.
     *
     * @param client The configured CSSBean used for access.
     *
     * @return An ORB instance configured for this client access.
     * @exception ConfigException
     */
    public ORB createNameServiceClientORB(CSSBean client)  throws ConfigException {
        return createORB(client.getURI(), (ORBConfiguration)client, translateToArgs(client), translateToNameServiceProps(client));
    }

    /**
     * Create a transient name service instance using the
     * specified host name and port.
     *
     * @param host   The String host name.
     * @param port   The port number of the listener.
     *
     * @return An opaque object that represents the name service.
     * @exception ConfigException
     */
    public Object createNameService(String host, int port) throws ConfigException {
        try {
            // create a name service using the supplied host and publish under the name "NameService"
            TransientNameService service = new TransientNameService(host, port, "NameService");
            service.run();
            // the service instance is returned as an opaque object.
            return service;
        } catch (TransientServiceException e) {
            throw new ConfigException("Error starting transient name service", e);
        }
    }

    /**
     * Destroy a name service instance created by a
     * prior call to createNameService().
     *
     * @param ns     The opaque name service object returned from a
     *               prior call to createNameService().
     */
    public void destroyNameService(Object ns) {
        // The name service instance handles its own shutdown.
        ((TransientNameService)ns).destroy();
    }

    /**
     * Static method used by SocketFactory instances to
     * retrieve the CORBABean or CSSBean that holds its
     * configuration information.  The String name has
     * been passed to the SocketFactory as part of its
     * initialization parameters.
     *
     * @param name   The name of the bean holding the configuration
     *               information.
     *
     * @return The bean mapping for this SocketFactory instance.
     */
    public static ORBConfiguration getConfiguration(String name) {
        return (ORBConfiguration)registry.get(name);
    }

    /**
     * Create an ORB instance using the configured argument
     * and property bundles.
     *
     * @param name   The String name of the configuration GBean used to
     *               create this ORB.
     * @param config The GBean configuration object required by the
     *               SocketFactory instance.
     * @param args   The String arguments passed to ORB.init().
     * @param props  The property bundle passed to ORB.init().
     *
     * @return An ORB constructed from the provided args and properties.
     */
    private ORB createORB(String name, ORBConfiguration config, String[] args, Properties props) {
        try {
            // we need to stuff this reference in the registry so that the SocketFactory can find it
            // when it initializes.
            registry.put(name, config);
            return ORB.init(args, props);

        } finally {
            // remove the configuration object from the registry now that the ORB has initialized.  We
            // don't want to create a memory leak on the GBean.
            registry.remove(name);
        }
    }

    /**
     * Translate a CORBABean configuration into an
     * array of arguments used to configure the ORB
     * instance.
     *
     * @param server The CORBABean we're creating an ORB instance for.
     *
     * @return A String{} array containing the initialization
     *         arguments.
     * @exception ConfigException
     */
    private String[] translateToArgs(CORBABean server) throws ConfigException {
        ArrayList list = new ArrayList();

        TSSConfig config = server.getTssConfig();

        // if the TSSConfig includes principal information, we need to add argument values
        // for this information.
        DefaultPrincipal principal = config.getDefaultPrincipal();
        if (principal != null) {
            if (principal instanceof DefaultRealmPrincipal) {
                DefaultRealmPrincipal realmPrincipal = (DefaultRealmPrincipal) principal;
                list.add("default-realm-principal::" + realmPrincipal.getRealm() + ":" + realmPrincipal.getDomain() + ":"
                         + realmPrincipal.getPrincipal().getClassName() + ":" + realmPrincipal.getPrincipal().getPrincipalName());
            } else if (principal instanceof DefaultDomainPrincipal) {
                DefaultDomainPrincipal domainPrincipal = (DefaultDomainPrincipal) principal;
                list.add("default-domain-principal::" + domainPrincipal.getDomain() + ":"
                         + domainPrincipal.getPrincipal().getClassName() + ":" + domainPrincipal.getPrincipal().getPrincipalName());
            } else {
                list.add("default-principal::" + principal.getPrincipal().getClassName() + ":" + principal.getPrincipal().getPrincipalName());
            }
        }

        // enable the connection plugin
        enableSocketFactory(server.getURI(), list);

        NameService nameService = server.getNameService();
        // if we have a name service to enable as an initial ref, add it to the init processing.
        if (nameService != null) {
            list.add("-ORBInitRef");
            list.add("NameService=" + nameService.getURI());
        }

        if (log.isDebugEnabled()) {
            for (Iterator iter = list.iterator(); iter.hasNext();) {
                log.debug(iter.next());
            }
        }

        return (String[]) list.toArray(new String[list.size()]);
    }

    private Properties translateToProps(CORBABean server) throws ConfigException {
        Properties result = new Properties();

        result.put("org.omg.CORBA.ORBClass", "org.apache.yoko.orb.CORBA.ORB");
        result.put("org.omg.CORBA.ORBSingletonClass", "org.apache.yoko.orb.CORBA.ORBSingleton");
        result.put("org.omg.PortableInterceptor.ORBInitializerClass.org.apache.geronimo.corba.transaction.TransactionInitializer", "");
        result.put("org.omg.PortableInterceptor.ORBInitializerClass.org.apache.geronimo.corba.security.SecurityInitializer", "");
        result.put("org.omg.PortableInterceptor.ORBInitializerClass.org.apache.geronimo.yoko.ORBInitializer", "");
        // don't specify the port if we're allowing this to default.
        if (server.getPort() > 0) {
            result.put("yoko.orb.oa.endpoint", "iiop --host " + server.getHost() + " --port " + server.getPort());
        }
        else {
            result.put("yoko.orb.oa.endpoint", "iiop --host " + server.getHost());
        }

        // check the tss config for a transport mech definition.  If we have one, then 
        // the port information will be passed in that config, and the port in the IIOP profile 
        // needs to be zero. 
        TSSConfig config = server.getTssConfig();
        TSSTransportMechConfig transportMech = config.getTransport_mech();
        if (transportMech != null) {
            if (transportMech instanceof TSSSSLTransportConfig) {
                result.put("yoko.orb.policy.zero_port", "true");
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("translateToProps(TSSConfig)");
            for (Enumeration iter = result.keys(); iter.hasMoreElements();) {
                String key = (String) iter.nextElement();
                log.debug(key + " = " + result.getProperty(key));
            }
        }
        return result;
    }

    /**
     * Translate a CSSBean configuration into the
     * argument bundle needed to instantiate the
     * ORB instance.
     *
     * @param client The CSSBean holding the configuration.
     *
     * @return A String array to be passed to ORB.init().
     * @exception ConfigException
     */
    private String[] translateToArgs(CSSBean client) throws ConfigException {
        ArrayList list = new ArrayList();

        // enable the connection plugin
        enableSocketFactory(client.getURI(), list);

        if (log.isDebugEnabled()) {
            for (Iterator iter = list.iterator(); iter.hasNext();) {
                log.debug(iter.next());
            }
        }

        return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Add arguments to the ORB.init() argument list
     * required to enable the SocketFactory used for
     * SSL support.
     *
     * @param uri    The URI name of the configuration GBean (either a
     *               CSSBean or a CORBABean).
     * @param args
     */
    private void enableSocketFactory(String uri, List args) {
        args.add("-IIOPconnectionHelper");
        args.add("org.apache.geronimo.yoko.SocketFactory");
        args.add("-IIOPconnectionHelperArgs");
        args.add(uri);
    }


    /**
     * Translate a CSSBean configuration into the
     * property bundle necessary to configure the
     * ORB instance.
     *
     * @param client The CSSBean holding the configuration.
     *
     * @return A property bundle that can be passed to ORB.init();
     * @exception ConfigException
     */
    private Properties translateToProps(CSSBean client) throws ConfigException {
        Properties result = new Properties();

        result.put("org.omg.CORBA.ORBClass", "org.apache.yoko.orb.CORBA.ORB");
        result.put("org.omg.CORBA.ORBSingletonClass", "org.apache.yoko.orb.CORBA.ORBSingleton");
        result.put("org.omg.PortableInterceptor.ORBInitializerClass.org.apache.geronimo.corba.transaction.TransactionInitializer", "");
        result.put("org.omg.PortableInterceptor.ORBInitializerClass.org.apache.geronimo.corba.security.SecurityInitializer", "");
        result.put("org.omg.PortableInterceptor.ORBInitializerClass.org.apache.geronimo.yoko.ORBInitializer", "");

        if (log.isDebugEnabled()) {
            log.debug("translateToProps(CSSConfig)");
            for (Enumeration iter = result.keys(); iter.hasMoreElements();) {
                String key = (String) iter.nextElement();
                log.debug(key + " = " + result.getProperty(key));
            }
        }
        return result;
    }


    /**
     * Translate a CSSBean configuration into the
     * property bundle necessary to configure the
     * ORB instance.
     *
     * @param client The CSSBean holding the configuration.
     *
     * @return A property bundle that can be passed to ORB.init();
     * @exception ConfigException
     */
    private Properties translateToNameServiceProps(CSSBean client) throws ConfigException {
        Properties result = new Properties();

        result.put("org.omg.CORBA.ORBClass", "org.apache.yoko.orb.CORBA.ORB");
        result.put("org.omg.CORBA.ORBSingletonClass", "org.apache.yoko.orb.CORBA.ORBSingleton");

        if (log.isDebugEnabled()) {
            log.debug("translateToNameServiceProps(CSSConfig)");
            for (Enumeration iter = result.keys(); iter.hasMoreElements();) {
                String key = (String) iter.nextElement();
                log.debug(key + " = " + result.getProperty(key));
            }
        }
        return result;
    }
}
