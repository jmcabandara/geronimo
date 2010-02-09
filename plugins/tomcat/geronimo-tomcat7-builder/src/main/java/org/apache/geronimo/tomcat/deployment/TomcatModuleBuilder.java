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

package org.apache.geronimo.tomcat.deployment;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

import javax.servlet.Servlet;

import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.deployment.Deployable;
import org.apache.geronimo.deployment.DeployableBundle;
import org.apache.geronimo.deployment.DeployableJarFile;
import org.apache.geronimo.deployment.ModuleIDBuilder;
import org.apache.geronimo.deployment.NamespaceDrivenBuilder;
import org.apache.geronimo.deployment.NamespaceDrivenBuilderCollection;
import org.apache.geronimo.deployment.service.EnvironmentBuilder;
import org.apache.geronimo.deployment.xbeans.EnvironmentType;
import org.apache.geronimo.deployment.xmlbeans.XmlBeansUtil;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.AbstractNameQuery;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.GBeanLifecycle;
import org.apache.geronimo.gbean.ReferencePatterns;
import org.apache.geronimo.j2ee.deployment.EARContext;
import org.apache.geronimo.j2ee.deployment.Module;
import org.apache.geronimo.j2ee.deployment.ModuleBuilder;
import org.apache.geronimo.j2ee.deployment.ModuleBuilderExtension;
import org.apache.geronimo.j2ee.deployment.NamingBuilder;
import org.apache.geronimo.j2ee.deployment.WebModule;
import org.apache.geronimo.j2ee.deployment.WebServiceBuilder;
import org.apache.geronimo.j2ee.deployment.annotation.AnnotatedWebApp;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.Naming;
import org.apache.geronimo.kernel.repository.Environment;
import org.apache.geronimo.kernel.util.JarUtils;
import org.apache.geronimo.naming.deployment.ENCConfigBuilder;
import org.apache.geronimo.naming.deployment.GBeanResourceEnvironmentBuilder;
import org.apache.geronimo.naming.deployment.ResourceEnvironmentSetter;
import org.apache.geronimo.security.jaas.ConfigurationFactory;
import org.apache.geronimo.security.jacc.ComponentPermissions;
import org.apache.geronimo.tomcat.LifecycleListenerGBean;
import org.apache.geronimo.tomcat.ManagerGBean;
import org.apache.geronimo.tomcat.RealmGBean;
import org.apache.geronimo.tomcat.TomcatWebAppContext;
import org.apache.geronimo.tomcat.ValveGBean;
import org.apache.geronimo.tomcat.cluster.CatalinaClusterGBean;
import org.apache.geronimo.tomcat.util.SecurityHolder;
import org.apache.geronimo.web.deployment.GenericToSpecificPlanConverter;
import org.apache.geronimo.web25.deployment.AbstractWebModuleBuilder;
import org.apache.geronimo.web25.deployment.security.AuthenticationWrapper;
import org.apache.geronimo.xbeans.geronimo.jaspi.JaspiAuthModuleType;
import org.apache.geronimo.xbeans.geronimo.jaspi.JaspiConfigProviderType;
import org.apache.geronimo.xbeans.geronimo.jaspi.JaspiServerAuthConfigType;
import org.apache.geronimo.xbeans.geronimo.jaspi.JaspiServerAuthContextType;
import org.apache.geronimo.xbeans.geronimo.web.tomcat.TomcatAuthenticationType;
import org.apache.geronimo.xbeans.geronimo.web.tomcat.TomcatWebAppDocument;
import org.apache.geronimo.xbeans.geronimo.web.tomcat.TomcatWebAppType;
import org.apache.geronimo.xbeans.geronimo.web.tomcat.config.GerTomcatDocument;
import org.apache.geronimo.xbeans.javaee6.ServletType;
import org.apache.geronimo.xbeans.javaee6.WebAppDocument;
import org.apache.geronimo.xbeans.javaee6.WebAppType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Rev:385659 $ $Date$
 */
public class TomcatModuleBuilder extends AbstractWebModuleBuilder implements GBeanLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TomcatModuleBuilder.class);
    static final String ROLE_MAPPER_DATA_NAME = "roleMapperDataName";

    private static final String TOMCAT_NAMESPACE = TomcatWebAppDocument.type.getDocumentElementName().getNamespaceURI();
    private static final Map<String, String> NAMESPACE_UPDATES = new HashMap<String, String>();
    static {
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/web", "http://geronimo.apache.org/xml/ns/j2ee/web-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/j2ee/web-1.1", "http://geronimo.apache.org/xml/ns/j2ee/web-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/j2ee/web-1.2", "http://geronimo.apache.org/xml/ns/j2ee/web-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/j2ee/web-2.0", "http://geronimo.apache.org/xml/ns/j2ee/web-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/web/tomcat", "http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-1.1", "http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-1.2", "http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-2.0", "http://geronimo.apache.org/xml/ns/j2ee/web/tomcat-2.0.1");
        NAMESPACE_UPDATES.put("http://geronimo.apache.org/xml/ns/web/tomcat/config", "http://geronimo.apache.org/xml/ns/j2ee/web/tomcat/config-1.0");
    }

    private final Environment defaultEnvironment;
    private final AbstractNameQuery tomcatContainerName;
    protected final NamespaceDrivenBuilderCollection clusteringBuilders;

    public TomcatModuleBuilder(Environment defaultEnvironment,
            AbstractNameQuery tomcatContainerName,
            Collection<WebServiceBuilder> webServiceBuilder,
            Collection<NamespaceDrivenBuilder> serviceBuilders,
            NamingBuilder namingBuilders,
            Collection<NamespaceDrivenBuilder> clusteringBuilders,
            Collection<ModuleBuilderExtension> moduleBuilderExtensions,
            ResourceEnvironmentSetter resourceEnvironmentSetter,
            Kernel kernel) {
        super(kernel, serviceBuilders, namingBuilders, resourceEnvironmentSetter, webServiceBuilder, moduleBuilderExtensions);
        this.defaultEnvironment = defaultEnvironment;
        this.clusteringBuilders = new NamespaceDrivenBuilderCollection(clusteringBuilders);
        this.tomcatContainerName = tomcatContainerName;
    }

    public void doStart() throws Exception {
        XmlBeansUtil.registerNamespaceUpdates(NAMESPACE_UPDATES);
    }

    public void doStop() {
        XmlBeansUtil.unregisterNamespaceUpdates(NAMESPACE_UPDATES);
    }

    public void doFail() {
        doStop();
    }

    public Module createModule(Bundle bundle, Naming naming, ModuleIDBuilder idBuilder) throws DeploymentException {
        if (bundle == null) {
            throw new NullPointerException("bundle is null");
        }
        String contextPath = (String) bundle.getHeaders().get("Web-ContextPath");
        if (contextPath == null) {
            // not for us
            return null;
        }
        
        String specDD = null;
        WebAppType webApp = null;
        
        URL specDDUrl = bundle.getEntry("WEB-INF/web.xml");
        if (specDDUrl == null) {
            webApp = WebAppType.Factory.newInstance();
        } else {
            try {
                specDD = JarUtils.readAll(specDDUrl);

                XmlObject parsed = XmlBeansUtil.parse(specDD);
                
                WebAppDocument webAppDoc = convertToServletSchema(parsed);
                webApp = webAppDoc.getWebApp();
                check(webApp);
            } catch (XmlException e) {
                throw new DeploymentException("Error parsing web.xml for " + bundle.getSymbolicName(), e);
            } catch (Exception e) {
                throw new DeploymentException("Error reading web.xml for " + bundle.getSymbolicName(), e);
            }
        }
        
        AbstractName earName = null;
        String targetPath = ".";   
        boolean standAlone = true;
        
        Deployable deployable = new DeployableBundle(bundle);
        // parse vendor dd
        TomcatWebAppType tomcatWebApp = getTomcatWebApp(null, deployable, standAlone, targetPath, webApp);

        EnvironmentType environmentType = tomcatWebApp.getEnvironment();
        Environment environment = EnvironmentBuilder.buildEnvironment(environmentType, defaultEnvironment);

        if (webApp.getDistributableArray().length == 1) {
            clusteringBuilders.buildEnvironment(tomcatWebApp, environment);
        }

        idBuilder.resolve(environment, bundle.getSymbolicName(), "wab");
        
        AbstractName moduleName;
        if (earName == null) {
            earName = naming.createRootName(environment.getConfigId(), NameFactory.NULL, NameFactory.J2EE_APPLICATION);
            moduleName = naming.createChildName(earName, environment.getConfigId().toString(), NameFactory.WEB_MODULE);
        } else {
            moduleName = naming.createChildName(earName, targetPath, NameFactory.WEB_MODULE);
        }

        // Create the AnnotatedApp interface for the WebModule
        AnnotatedWebApp annotatedWebApp = new AnnotatedWebApp(webApp);

        WebModule module = new WebModule(standAlone, moduleName, environment, deployable, targetPath, webApp, tomcatWebApp, specDD, contextPath, TOMCAT_NAMESPACE, annotatedWebApp);
        for (ModuleBuilderExtension mbe : moduleBuilderExtensions) {
            mbe.createModule(module, bundle, naming, idBuilder);
        }
        return module;
    }
    
    protected Module createModule(Object plan, JarFile moduleFile, String targetPath, URL specDDUrl, boolean standAlone, String contextRoot, AbstractName earName, Naming naming, ModuleIDBuilder idBuilder) throws DeploymentException {
        assert moduleFile != null : "moduleFile is null";
        assert targetPath != null : "targetPath is null";
        assert !targetPath.endsWith("/") : "targetPath must not end with a '/'";

        // parse the spec dd
        String specDD = null;
        WebAppType webApp = null;
        try {
            if (specDDUrl == null) {
                specDDUrl = JarUtils.createJarURL(moduleFile, "WEB-INF/web.xml");
            }

            // read in the entire specDD as a string, we need this for getDeploymentDescriptor
            // on the J2ee management object
            specDD = JarUtils.readAll(specDDUrl);

            // we found web.xml, if it won't parse that's an error.
            XmlObject parsed = XmlBeansUtil.parse(specDD);

            WebAppDocument webAppDoc = convertToServletSchema(parsed);
            webApp = webAppDoc.getWebApp();
            check(webApp);
        } catch (XmlException e) {
            // Output the target path in the error to make it clearer to the user which webapp
            // has the problem.  The targetPath is used, as moduleFile may have an unhelpful
            // value such as C:\geronimo-1.1\var\temp\geronimo-deploymentUtil22826.tmpdir
            throw new DeploymentException("Error parsing web.xml for " + targetPath, e);
        } catch (Exception e) {
            if (!moduleFile.getName().endsWith(".war")) {
                //not for us
                return null;
            }
            //else ignore as jee5 allows optional spec dd for .war's
        }

        if (webApp == null) {
            webApp = WebAppType.Factory.newInstance();
        }
        
        Deployable deployable = new DeployableJarFile(moduleFile);
        // parse vendor dd
        TomcatWebAppType tomcatWebApp = getTomcatWebApp(plan, deployable, standAlone, targetPath, webApp);
        contextRoot = getContextRoot(tomcatWebApp, contextRoot, webApp, standAlone, moduleFile, targetPath);

        EnvironmentType environmentType = tomcatWebApp.getEnvironment();
        Environment environment = EnvironmentBuilder.buildEnvironment(environmentType, defaultEnvironment);

        if (webApp.getDistributableArray().length == 1) {
            clusteringBuilders.buildEnvironment(tomcatWebApp, environment);
        }

        // Note: logic elsewhere depends on the default artifact ID being the file name less extension (ConfigIDExtractor)
        String warName = new File(moduleFile.getName()).getName();
        if (warName.lastIndexOf('.') > -1) {
            warName = warName.substring(0, warName.lastIndexOf('.'));
        }
        idBuilder.resolve(environment, warName, "war");

        AbstractName moduleName;
        if (earName == null) {
            earName = naming.createRootName(environment.getConfigId(), NameFactory.NULL, NameFactory.J2EE_APPLICATION);
            moduleName = naming.createChildName(earName, environment.getConfigId().toString(), NameFactory.WEB_MODULE);
        } else {
            moduleName = naming.createChildName(earName, targetPath, NameFactory.WEB_MODULE);
        }

        // Create the AnnotatedApp interface for the WebModule
        AnnotatedWebApp annotatedWebApp = new AnnotatedWebApp(webApp);

        WebModule module = new WebModule(standAlone, moduleName, environment, deployable, targetPath, webApp, tomcatWebApp, specDD, contextRoot, TOMCAT_NAMESPACE, annotatedWebApp);
        for (ModuleBuilderExtension mbe : moduleBuilderExtensions) {
            mbe.createModule(module, plan, moduleFile, targetPath, specDDUrl, environment, contextRoot, earName, naming, idBuilder);
        }
        return module;
    }

    private String getContextRoot(TomcatWebAppType tomcatWebApp, String contextRoot, WebAppType webApp, boolean standAlone, JarFile moduleFile, String targetPath) {
        //If we have a context root, override everything
        if (tomcatWebApp.isSetContextRoot()) {
            contextRoot = tomcatWebApp.getContextRoot();
        } else if (contextRoot == null || contextRoot.trim().equals("")) {
            //Otherwise if no contextRoot was passed in from the ear, then make up a default
            contextRoot = determineDefaultContextRoot(webApp, standAlone, moduleFile, targetPath);
        }
        contextRoot = contextRoot.trim();
        if (contextRoot.length() > 0) {
            // only force the context root to start with a forward slash
            // if it is not null
            if (!contextRoot.startsWith("/")) {
                //I'm not sure if we should always fix up peculiar context roots.
                contextRoot = "/" + contextRoot;
            }
        }
        return contextRoot;
    }


    TomcatWebAppType getTomcatWebApp(Object plan, Deployable deployable, boolean standAlone, String targetPath, WebAppType webApp) throws DeploymentException {
        XmlObject rawPlan = null;
        try {
            // load the geronimo-web.xml from either the supplied plan or from the earFile
            try {
                if (plan instanceof XmlObject) {
                    rawPlan = (XmlObject) plan;
                } else {
                    if (plan != null) {
                        rawPlan = XmlBeansUtil.parse(((File) plan).toURL(), getClass().getClassLoader());
                    } else {
                        URL path = deployable.getResource("WEB-INF/geronimo-web.xml");
                        if (path == null) {
                            path = deployable.getResource("WEB-INF/geronimo-tomcat.xml");
                        }
                        if (path == null) {
                            log.warn("Web application " + targetPath + " does not contain a WEB-INF/geronimo-web.xml deployment plan.  This may or may not be a problem, depending on whether you have things like resource references that need to be resolved.  You can also give the deployer a separate deployment plan file on the command line.");                            
                        } else {
                            rawPlan = XmlBeansUtil.parse(path, getClass().getClassLoader());
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to load geronimo-web.xml", e);
            }

            TomcatWebAppType tomcatWebApp;
            if (rawPlan != null) {
                XmlObject webPlan = new GenericToSpecificPlanConverter(GerTomcatDocument.type.getDocumentElementName().getNamespaceURI(),
                        TomcatWebAppDocument.type.getDocumentElementName().getNamespaceURI(), "tomcat").convertToSpecificPlan(rawPlan);
                tomcatWebApp = (TomcatWebAppType) webPlan.changeType(TomcatWebAppType.type);
                XmlBeansUtil.validateDD(tomcatWebApp);
            } else {
                tomcatWebApp = createDefaultPlan();
            }
            return tomcatWebApp;
        } catch (XmlException e) {
            throw new DeploymentException("xml problem for web app " + targetPath, e);
        }
    }

    private TomcatWebAppType createDefaultPlan() {
        return TomcatWebAppType.Factory.newInstance();
    }


    public void initContext(EARContext earContext, Module module, Bundle bundle) throws DeploymentException {
        TomcatWebAppType gerWebApp = (TomcatWebAppType) module.getVendorDD();
        boolean hasSecurityRealmName = gerWebApp.isSetSecurityRealmName();
        basicInitContext(earContext, module, gerWebApp, hasSecurityRealmName);
        for (ModuleBuilderExtension mbe : moduleBuilderExtensions) {
            mbe.initContext(earContext, module, bundle);
        }
    }

    public void addGBeans(EARContext earContext, Module module, Bundle bundle, Collection repository) throws DeploymentException {
        EARContext moduleContext = module.getEarContext();
        Bundle webBundle = moduleContext.getDeploymentBundle();
        AbstractName moduleName = module.getModuleName();
        WebModule webModule = (WebModule) module;

        WebAppType webApp = (WebAppType) webModule.getSpecDD();

        TomcatWebAppType tomcatWebApp = (TomcatWebAppType) webModule.getVendorDD();

        GBeanData webModuleData = new GBeanData(moduleName, TomcatWebAppContext.class);
        configureBasicWebModuleAttributes(webApp, tomcatWebApp, moduleContext, earContext, webModule, webModuleData);
        String contextPath = webModule.getContextRoot();
        if (!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }
        try {
            moduleContext.addGBean(webModuleData);
            webModuleData.setAttribute("contextPath", contextPath);
            // unsharableResources, applicationManagedSecurityResources
            GBeanResourceEnvironmentBuilder rebuilder = new GBeanResourceEnvironmentBuilder(webModuleData);
            //N.B. use earContext not moduleContext
            resourceEnvironmentSetter.setResourceEnvironment(rebuilder, webApp.getResourceRefArray(), tomcatWebApp.getResourceRefArray());

            if (tomcatWebApp.isSetWebContainer()) {
                AbstractNameQuery webContainerName = ENCConfigBuilder.getGBeanQuery(GBeanInfoBuilder.DEFAULT_J2EE_TYPE, tomcatWebApp.getWebContainer());
                webModuleData.setReferencePattern("Container", webContainerName);
            } else {
                webModuleData.setReferencePattern("Container", tomcatContainerName);
            }

            //get Tomcat display-name
            if (webApp.getDisplayNameArray().length > 0) {
                webModuleData.setAttribute("displayName", webApp.getDisplayNameArray()[0].getStringValue());
            }

            // Process the Tomcat container-config elements
            if (tomcatWebApp.isSetHost()) {
                String virtualServer = tomcatWebApp.getHost().trim();
                webModuleData.setAttribute("virtualServer", virtualServer);
            }
            if (tomcatWebApp.isSetCrossContext()) {
                webModuleData.setAttribute("crossContext", Boolean.TRUE);
            }
            if (tomcatWebApp.isSetWorkDir()) {
                String workDir = tomcatWebApp.getWorkDir();
                webModuleData.setAttribute("workDir", workDir);
            }
            if (tomcatWebApp.isSetDisableCookies()) {
                webModuleData.setAttribute("disableCookies", Boolean.TRUE);
            }
            if (tomcatWebApp.isSetTomcatRealm()) {
                String tomcatRealm = tomcatWebApp.getTomcatRealm().trim();
                AbstractName realmName = earContext.getNaming().createChildName(moduleName, tomcatRealm, RealmGBean.GBEAN_INFO.getJ2eeType());
                webModuleData.setReferencePattern("TomcatRealm", realmName);
            }
            if (tomcatWebApp.isSetValveChain()) {
                String valveChain = tomcatWebApp.getValveChain().trim();
                AbstractName valveName = earContext.getNaming().createChildName(moduleName, valveChain, ValveGBean.J2EE_TYPE);
                webModuleData.setReferencePattern("TomcatValveChain", valveName);
            }

            if (tomcatWebApp.isSetListenerChain()) {
                String listenerChain = tomcatWebApp.getListenerChain().trim();
                AbstractName listenerName = earContext.getNaming().createChildName(moduleName, listenerChain, LifecycleListenerGBean.J2EE_TYPE);
                webModuleData.setReferencePattern("LifecycleListenerChain", listenerName);
            }

            if (tomcatWebApp.isSetCluster()) {
                String cluster = tomcatWebApp.getCluster().trim();
                AbstractName clusterName = earContext.getNaming().createChildName(moduleName, cluster, CatalinaClusterGBean.J2EE_TYPE);
                webModuleData.setReferencePattern("Cluster", clusterName);
            }

            if (tomcatWebApp.isSetManager()) {
                String manager = tomcatWebApp.getManager().trim();
                AbstractName managerName = earContext.getNaming().createChildName(moduleName, manager, ManagerGBean.J2EE_TYPE);
                webModuleData.setReferencePattern(TomcatWebAppContext.GBEAN_REF_MANAGER_RETRIEVER, managerName);
            }

            Boolean distributable = webApp.getDistributableArray().length == 1 ? TRUE : FALSE;
            if (TRUE == distributable) {
                clusteringBuilders.build(tomcatWebApp, earContext, moduleContext);
                if (null == webModuleData.getReferencePatterns(TomcatWebAppContext.GBEAN_REF_CLUSTERED_VALVE_RETRIEVER)) {
                    log.warn("No clustering builders configured: app will not be clustered");
                }
            }

            Collection<String> listeners = new ArrayList<String>();
            webModuleData.setAttribute("listenerClassNames", listeners);
            
            //Handle the role permissions and webservices on the servlets.
            ServletType[] servletTypes = webApp.getServletArray();
            Map<String, AbstractName> webServices = new HashMap<String, AbstractName>();
            Class baseServletClass;
            try {
                baseServletClass = webBundle.loadClass(Servlet.class.getName());
            } catch (ClassNotFoundException e) {
                throw new DeploymentException("Could not load javax.servlet.Servlet in bundle " + bundle, e);
            }
            for (ServletType servletType : servletTypes) {

                if (servletType.isSetServletClass()) {
                    String servletName = servletType.getServletName().getStringValue().trim();
                    String servletClassName = servletType.getServletClass().getStringValue().trim();
                    Class servletClass;
                    try {
                        servletClass = webBundle.loadClass(servletClassName);
                    } catch (ClassNotFoundException e) {
                        throw new DeploymentException("Could not load servlet class " + servletClassName + " from bundle " + bundle, e);
                    }
                    if (!baseServletClass.isAssignableFrom(servletClass)) {
                        //fake servletData
                        AbstractName servletAbstractName = moduleContext.getNaming().createChildName(moduleName, servletName, NameFactory.SERVLET);
                        GBeanData servletData = new GBeanData();
                        servletData.setAbstractName(servletAbstractName);
                        //let the web service builder deal with configuring the gbean with the web service stack
                        //Here we just extract the factory reference
                        boolean configured = false;
                        for (WebServiceBuilder serviceBuilder : webServiceBuilder) {
                            if (serviceBuilder.configurePOJO(servletData, servletName, module, servletClassName, moduleContext)) {
                                configured = true;
                                break;
                            }
                        }
                        if (!configured) {
                            throw new DeploymentException("POJO web service: " + servletName + " not configured by any web service builder");
                        }
                        ReferencePatterns patterns = servletData.getReferencePatterns("WebServiceContainerFactory");
                        AbstractName wsContainerFactoryName = patterns.getAbstractName();
                        webServices.put(servletName, wsContainerFactoryName);
                        //force all the factories to start before the web app that needs them.
                        webModuleData.addDependency(wsContainerFactoryName);
                    }

                }
            }


            webModuleData.setAttribute("webServices", webServices);

            if (tomcatWebApp.isSetSecurityRealmName()) {
                if (earContext.getSecurityConfiguration() == null) {
                    throw new DeploymentException("You have specified a <security-realm-name> for the webapp " + moduleName + " but no <security> configuration (role mapping) is supplied in the Geronimo plan for the web application (or the Geronimo plan for the EAR if the web app is in an EAR)");
                }

                SecurityHolder securityHolder = new SecurityHolder();
                String securityRealmName = tomcatWebApp.getSecurityRealmName().trim();

                webModuleData.setReferencePattern("RunAsSource", (AbstractNameQuery)earContext.getGeneralData().get(ROLE_MAPPER_DATA_NAME));
                webModuleData.setReferencePattern("ConfigurationFactory", new AbstractNameQuery(null, Collections.singletonMap("name", securityRealmName), ConfigurationFactory.class.getName()));

                /**
                 * TODO - go back to commented version when possible.
                 */
                String policyContextID = moduleName.toString().replaceAll("[, :]", "_");
                securityHolder.setPolicyContextID(policyContextID);

                ComponentPermissions componentPermissions = buildSpecSecurityConfig(webApp);
                earContext.addSecurityContext(policyContextID, componentPermissions);
                //TODO WTF is this for?
                securityHolder.setSecurity(true);

                webModuleData.setAttribute("securityHolder", securityHolder);
                //local jaspic configuration
                if (tomcatWebApp.isSetAuthentication()) {
                    AuthenticationWrapper authType = new TomcatAuthenticationWrapper(tomcatWebApp.getAuthentication());
                    configureLocalJaspicProvider(authType, contextPath, module, webModuleData);
                }

            }

            //listeners added directly to the StandardContext will get loaded by the tomcat classloader, not the app classloader!
            //TODO this may definitely not be the best place for this!
            for (ModuleBuilderExtension mbe : moduleBuilderExtensions) {
                mbe.addGBeans(earContext, module, bundle, repository);
            }
            //not truly metadata complete until MBEs have run
            if (!webApp.getMetadataComplete()) {
                webApp.setMetadataComplete(true);
                module.setOriginalSpecDD(getSpecDDAsString(webModule));
            }
            webModuleData.setAttribute("deploymentDescriptor", module.getOriginalSpecDD());
            
            module.addAsChildConfiguration();
        } catch (DeploymentException de) {
            throw de;
        } catch (Exception e) {
            throw new DeploymentException("Unable to initialize GBean for web app " + module.getName(), e);
        }
    }
    
    public String getSchemaNamespace() {
        return TOMCAT_NAMESPACE;
    }

    private static class TomcatAuthenticationWrapper implements AuthenticationWrapper {
        private final TomcatAuthenticationType authType;

        private TomcatAuthenticationWrapper(TomcatAuthenticationType authType) {
            this.authType = authType;
        }

        public JaspiConfigProviderType getConfigProvider() {
            return authType.getConfigProvider();
        }

        public boolean isSetConfigProvider() {
            return authType.isSetConfigProvider();
        }

        public JaspiServerAuthConfigType getServerAuthConfig() {
            return authType.getServerAuthConfig();
        }

        public boolean isSetServerAuthConfig() {
            return authType.isSetServerAuthConfig();
        }

        public JaspiServerAuthContextType getServerAuthContext() {
            return authType.getServerAuthContext();
        }

        public boolean isSetServerAuthContext() {
            return authType.isSetServerAuthContext();
        }

        public JaspiAuthModuleType getServerAuthModule() {
            return authType.getServerAuthModule();
        }

        public boolean isSetServerAuthModule() {
            return authType.isSetServerAuthModule();
        }
    }



    public static final GBeanInfo GBEAN_INFO;
    public static final String GBEAN_REF_CLUSTERING_BUILDERS = "ClusteringBuilders";

    static {
        GBeanInfoBuilder infoBuilder = GBeanInfoBuilder.createStatic(TomcatModuleBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addAttribute("defaultEnvironment", Environment.class, true, true);
        infoBuilder.addAttribute("tomcatContainerName", AbstractNameQuery.class, true, true);
        infoBuilder.addReference("WebServiceBuilder", WebServiceBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addReference("ServiceBuilders", NamespaceDrivenBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addReference("NamingBuilders", NamingBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addReference(GBEAN_REF_CLUSTERING_BUILDERS, NamespaceDrivenBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addReference("ModuleBuilderExtensions", ModuleBuilderExtension.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addReference("ResourceEnvironmentSetter", ResourceEnvironmentSetter.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addAttribute("kernel", Kernel.class, false);
        infoBuilder.addInterface(ModuleBuilder.class);

        infoBuilder.setConstructor(new String[]{
                "defaultEnvironment",
                "tomcatContainerName",
                "WebServiceBuilder",
                "ServiceBuilders",
                "NamingBuilders",
                GBEAN_REF_CLUSTERING_BUILDERS,
                "ModuleBuilderExtensions",
                "ResourceEnvironmentSetter",
                "kernel"});
        GBEAN_INFO = infoBuilder.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }

}