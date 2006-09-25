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
package org.apache.geronimo.axis.builder;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import javax.wsdl.Binding;
import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.xml.namespace.QName;
import javax.xml.rpc.handler.HandlerInfo;

import org.apache.axis.constants.Style;
import org.apache.axis.description.JavaServiceDesc;
import org.apache.axis.handlers.HandlerInfoChainFactory;
import org.apache.axis.handlers.soap.SOAPService;
import org.apache.axis.providers.java.RPCProvider;
import org.apache.axis.soap.SOAPConstants;
import org.apache.geronimo.axis.client.AxisServiceReference;
import org.apache.geronimo.axis.client.OperationInfo;
import org.apache.geronimo.axis.client.SEIFactory;
import org.apache.geronimo.axis.client.SEIFactoryImpl;
import org.apache.geronimo.axis.server.AxisWebServiceContainer;
import org.apache.geronimo.axis.server.POJOProvider;
import org.apache.geronimo.axis.server.ServiceInfo;
import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.deployment.DeployableModule;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.j2ee.deployment.Module;
import org.apache.geronimo.j2ee.deployment.WebServiceBuilder;
import org.apache.geronimo.j2ee.deployment.HandlerInfoInfo;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.xbeans.geronimo.naming.GerPortCompletionType;
import org.apache.geronimo.xbeans.geronimo.naming.GerPortType;
import org.apache.geronimo.xbeans.geronimo.naming.GerServiceCompletionType;
import org.apache.geronimo.xbeans.geronimo.naming.GerServiceRefType;
import org.apache.geronimo.xbeans.j2ee.JavaWsdlMappingType;
import org.apache.geronimo.xbeans.j2ee.JavaXmlTypeMappingType;
import org.apache.geronimo.xbeans.j2ee.ServiceEndpointInterfaceMappingType;
import org.apache.geronimo.xbeans.j2ee.ServiceEndpointMethodMappingType;
import org.apache.geronimo.deployment.util.DeploymentUtil;

/**
 * @version $Rev$ $Date$
 */
public class AxisBuilder implements WebServiceBuilder {

    private static final SOAPConstants SOAP_VERSION = SOAPConstants.SOAP11_CONSTANTS;

    public Map findWebServices(DeployableModule moduleFile, boolean isEJB, Map servletLocations) throws DeploymentException {
        final String path = isEJB ? "META-INF/webservices.xml" : "WEB-INF/webservices.xml";
        try {
            URL wsDDUrl = DeploymentUtil.createJarURL(moduleFile, path);
            Map result = WSDescriptorParser.parseWebServiceDescriptor(wsDDUrl, moduleFile, isEJB, servletLocations);
            if (result != null) {
                return result;
            }
        } catch (MalformedURLException e) {
            // The webservices.xml file doesn't exist.
        }
        return Collections.EMPTY_MAP;
    }

    public void configurePOJO(GBeanData targetGBean, DeployableModule deployableModule, Object portInfoObject, String seiClassName, ClassLoader classLoader) throws DeploymentException {
        PortInfo portInfo = (PortInfo) portInfoObject;
        ServiceInfo serviceInfo = AxisServiceBuilder.createServiceInfo(portInfo, classLoader);
        JavaServiceDesc serviceDesc = serviceInfo.getServiceDesc();

        try {
            classLoader.loadClass(seiClassName);
        } catch (ClassNotFoundException e) {
            throw new DeploymentException("Unable to load servlet class for pojo webservice: " + seiClassName, e);
        }

        targetGBean.setAttribute("pojoClassName", seiClassName);
        RPCProvider provider = new POJOProvider();

        SOAPService service = new SOAPService(null, provider, null);
        service.setServiceDescription(serviceDesc);
        service.setOption("className", seiClassName);

        HandlerInfoChainFactory handlerInfoChainFactory = new HandlerInfoChainFactory(serviceInfo.getHandlerInfos());
        service.setOption(org.apache.axis.Constants.ATTR_HANDLERINFOCHAIN, handlerInfoChainFactory);

        URI location;
        try {
            location = new URI(serviceDesc.getEndpointURL());
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid webservice endpoint URI", e);
        }
        URI wsdlURI;
        try {
            wsdlURI = new URI(serviceDesc.getWSDLFile());
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid wsdl URI", e);

        }

        AxisWebServiceContainer axisWebServiceContainer = new AxisWebServiceContainer(location, wsdlURI, service, serviceInfo.getWsdlMap(), classLoader);
        targetGBean.setAttribute("webServiceContainer", axisWebServiceContainer);
    }

    public void configureEJB(GBeanData targetGBean, DeployableModule deployableModule, Object portInfoObject, ClassLoader classLoader) throws DeploymentException {
        PortInfo portInfo = (PortInfo) portInfoObject;
        ServiceInfo serviceInfo = AxisServiceBuilder.createServiceInfo(portInfo, classLoader);
        targetGBean.setAttribute("serviceInfo", serviceInfo);
        JavaServiceDesc serviceDesc = serviceInfo.getServiceDesc();
       URI location = portInfo.getContextURI();
        targetGBean.setAttribute("location", location);
        URI wsdlURI;
        try {
            wsdlURI = new URI(serviceDesc.getWSDLFile());
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid wsdl URI", e);
        }
        targetGBean.setAttribute("wsdlURI", wsdlURI);
    }


    //ServicereferenceBuilder
    public Object createService(Class serviceInterface, URI wsdlURI, URI jaxrpcMappingURI, QName serviceQName, Map portComponentRefMap, List handlerInfos, Object serviceRefType, Module module, ClassLoader classLoader) throws DeploymentException {
        GerServiceRefType gerServiceRefType = (GerServiceRefType) serviceRefType;
        DeployableModule deployableModule = module.getModuleFile();
        SchemaInfoBuilder schemaInfoBuilder = null;
        JavaWsdlMappingType mapping = null;
        if (wsdlURI != null) {
            schemaInfoBuilder = new SchemaInfoBuilder(deployableModule, wsdlURI);

            mapping = WSDescriptorParser.readJaxrpcMapping(deployableModule, jaxrpcMappingURI);
        }

        return createService(serviceInterface, schemaInfoBuilder, mapping, serviceQName, SOAP_VERSION, handlerInfos, gerServiceRefType, module, classLoader);
    }

    public Object createService(Class serviceInterface, SchemaInfoBuilder schemaInfoBuilder, JavaWsdlMappingType mapping, QName serviceQName, SOAPConstants soapVersion, List handlerInfos, GerServiceRefType serviceRefType, Module module, ClassLoader classloader) throws DeploymentException {
        Map seiPortNameToFactoryMap = new HashMap();
        Map seiClassNameToFactoryMap = new HashMap();
        if (schemaInfoBuilder != null) {
            buildSEIFactoryMap(schemaInfoBuilder, serviceRefType, mapping, handlerInfos, serviceQName, soapVersion, seiPortNameToFactoryMap, seiClassNameToFactoryMap, classloader);
        }
        return new AxisServiceReference(serviceInterface.getName(), seiPortNameToFactoryMap, seiClassNameToFactoryMap);
    }

/*
    public Object createServiceInterfaceProxy(Class serviceInterface, Map seiPortNameToFactoryMap, Map seiClassNameToFactoryMap, DeploymentContext deploymentContext, Module module, ClassLoader classLoader) throws DeploymentException {

        Callback callback = new ServiceMethodInterceptor(seiPortNameToFactoryMap);
        Callback[] methodInterceptors = new Callback[]{SerializableNoOp.INSTANCE, callback};

        Enhancer enhancer = new Enhancer();
        enhancer.setClassLoader(classLoader);
        enhancer.setSuperclass(ServiceImpl.class);
        enhancer.setInterfaces(new Class[]{serviceInterface});
        enhancer.setCallbackFilter(new NoOverrideCallbackFilter(Service.class));
        enhancer.setCallbackTypes(new Class[]{NoOp.class, MethodInterceptor.class});
        enhancer.setUseFactory(false);
        ByteArrayRetrievingGeneratorStrategy strategy = new ByteArrayRetrievingGeneratorStrategy();
        enhancer.setStrategy(strategy);
        enhancer.setUseCache(false);
        Class serviceClass = enhancer.createClass();

        try {
            module.addClass(serviceClass.getName(), strategy.getClassBytes(), deploymentContext);
        } catch (IOException e) {
            throw new DeploymentException("Could not write out class bytes", e);
        } catch (URISyntaxException e) {
            throw new DeploymentException("Could not constuct URI for location of enhanced class", e);
        }
        Enhancer.registerCallbacks(serviceClass, methodInterceptors);
        FastConstructor constructor = FastClass.create(serviceClass).getConstructor(SERVICE_CONSTRUCTOR_TYPES);
        try {
            return constructor.newInstance(new Object[]{seiPortNameToFactoryMap, seiClassNameToFactoryMap});
        } catch (InvocationTargetException e) {
            throw new DeploymentException("Could not construct service instance", e.getTargetException());
        }
    }
*/

    public void buildSEIFactoryMap(SchemaInfoBuilder schemaInfoBuilder, GerServiceRefType serviceRefType, JavaWsdlMappingType mapping, List handlerInfos, QName serviceQName, SOAPConstants soapVersion, Map seiPortNameToFactoryMap, Map seiClassNameToFactoryMap, ClassLoader classLoader) throws DeploymentException {
        Map exceptionMap = WSDescriptorParser.getExceptionMap(mapping);

        Definition definition = schemaInfoBuilder.getDefinition();
        //check for consistency
        if (definition.getServices().size() == 0) {
            //partial wsdl
            if (serviceRefType == null || !serviceRefType.isSetServiceCompletion()) {
                throw new DeploymentException("Partial wsdl, but no service completion supplied");
            }
            GerServiceCompletionType serviceCompletion = serviceRefType.getServiceCompletion();
            String serviceLocalName = serviceCompletion.getServiceName().trim();
            String namespace = definition.getTargetNamespace();
            serviceQName = new QName(namespace, serviceLocalName);
            javax.wsdl.Service service = definition.createService();
            service.setQName(serviceQName);
            GerPortCompletionType[] portCompletions = serviceCompletion.getPortCompletionArray();
            for (int i = 0; i < portCompletions.length; i++) {
                GerPortCompletionType portCompletion = portCompletions[i];
                GerPortType port = portCompletion.getPort();
                URL location = getLocation(port);
                String portName = port.getPortName().trim();
                String bindingName = portCompletion.getBindingName().trim();
                QName bindingQName = new QName(namespace, bindingName);
                Binding binding = definition.getBinding(bindingQName);
                if (binding == null) {
                    throw new DeploymentException("No binding found with qname: " + bindingQName);
                }
                String credentialsName = port.isSetCredentialsName() ? port.getCredentialsName().trim() : null;
                mapBinding(binding, mapping, serviceQName, classLoader, soapVersion, schemaInfoBuilder, portName, location, handlerInfos, seiPortNameToFactoryMap, seiClassNameToFactoryMap, credentialsName, exceptionMap);

            }
        } else {
            //full wsdl
            if (serviceRefType != null && serviceRefType.isSetServiceCompletion()) {
                throw new DeploymentException("Full wsdl, but service completion supplied");
            }
            //organize the extra port info
            Map portMap = new HashMap();
            if (serviceRefType != null) {
                GerPortType[] ports = serviceRefType.getPortArray();
                for (int i = 0; i < ports.length; i++) {
                    GerPortType port = ports[i];
                    String portName = port.getPortName().trim();
                    portMap.put(portName, port);
                }
            }

            //find the service we are working with
            javax.wsdl.Service service = getService(serviceQName, schemaInfoBuilder.getDefinition());
            if (serviceQName == null) {
                serviceQName = service.getQName();
            }

            Map wsdlPortMap = service.getPorts();
            for (Iterator iterator = wsdlPortMap.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String portName = (String) entry.getKey();
                Port port = (Port) entry.getValue();

                GerPortType gerPort = (GerPortType) portMap.get(portName);

                URL location = gerPort == null ? getAddressLocation(port) : getLocation(gerPort);
                //skip non-soap ports
                if (location == null) {
                    continue;
                }
                String credentialsName = gerPort == null || gerPort.getCredentialsName() == null ? null : gerPort.getCredentialsName().trim();

                Binding binding = port.getBinding();

                mapBinding(binding, mapping, serviceQName, classLoader, soapVersion, schemaInfoBuilder, portName, location, handlerInfos, seiPortNameToFactoryMap, seiClassNameToFactoryMap, credentialsName, exceptionMap);
            }
        }
    }

    private void mapBinding(Binding binding, JavaWsdlMappingType mapping, QName serviceQName, ClassLoader classLoader, SOAPConstants soapVersion, SchemaInfoBuilder schemaInfoBuilder, String portName, URL location, List handlerInfos, Map seiPortNameToFactoryMap, Map seiClassNameToFactoryMap, String credentialsName, Map exceptionMap) throws DeploymentException {
        Style portStyle = getStyle(binding);

        PortType portType = binding.getPortType();

        ServiceEndpointInterfaceMappingType[] endpointMappings = mapping.getServiceEndpointInterfaceMappingArray();


        //port type corresponds to SEI
        List operations = portType.getOperations();
        OperationInfo[] operationInfos = new OperationInfo[operations.size()];
        if (endpointMappings.length == 0) {
            doLightweightMapping(serviceQName, portType, mapping, classLoader, operations, binding, portStyle, soapVersion, operationInfos, schemaInfoBuilder, portName, location, handlerInfos, seiPortNameToFactoryMap, seiClassNameToFactoryMap, credentialsName);
        } else {
            doHeavyweightMapping(serviceQName, portType, endpointMappings, classLoader, operations, binding, portStyle, soapVersion, exceptionMap, schemaInfoBuilder, mapping, operationInfos, portName, location, handlerInfos, seiPortNameToFactoryMap, seiClassNameToFactoryMap, credentialsName);
        }
    }

    private URL getLocation(GerPortType port) throws DeploymentException {
        String protocol = port.getProtocol().trim();
        String host = port.getHost().trim();
        int portNum = port.getPort();
        String uri = port.getUri().trim();
        String locationURIString = protocol + "://" + host + ":" + portNum + uri;
        URL location;
        try {
            location = new URL(locationURIString);
        } catch (MalformedURLException e) {
            throw new DeploymentException("Could not construct web service location URL from " + locationURIString);
        }
        return location;
    }

    private javax.wsdl.Service getService(QName serviceQName, Definition definition) throws DeploymentException {
        javax.wsdl.Service service;
        if (serviceQName != null) {
            service = definition.getService(serviceQName);
            if (service == null) {
                throw new DeploymentException("No service wsdl for supplied service qname " + serviceQName);
            }
        } else {
            Map services = definition.getServices();
            if (services.size() > 1) {
                throw new DeploymentException("no serviceQName supplied, and there are " + services.size() + " services");
            }
            if (services.size() == 0) {
                throw new DeploymentException("No service in wsdl, and no service completion supplied!");
            } else {
                service = (javax.wsdl.Service) services.values().iterator().next();
            }
        }
        return service;
    }

    private Style getStyle(Binding binding) throws DeploymentException {
        SOAPBinding soapBinding = (SOAPBinding) SchemaInfoBuilder.getExtensibilityElement(SOAPBinding.class, binding.getExtensibilityElements());
//            String transportURI = soapBinding.getTransportURI();
        String portStyleString = soapBinding.getStyle();
        return Style.getStyle(portStyleString);
    }

    private URL getAddressLocation(Port port) throws DeploymentException {
        SOAPAddress soapAddress;
        try {
            soapAddress = (SOAPAddress) SchemaInfoBuilder.getExtensibilityElement(SOAPAddress.class, port.getExtensibilityElements());
        } catch (DeploymentException e) {
            //a http: protocol REST service.  Skip it.
            return null;
        }
        String locationURIString = soapAddress.getLocationURI();
        URL location;
        try {
            location = new URL(locationURIString);
        } catch (MalformedURLException e) {
            throw new DeploymentException("Could not construct web service location URL from " + locationURIString);
        }
        return location;
    }

    private void doHeavyweightMapping(QName serviceName, PortType portType, ServiceEndpointInterfaceMappingType[] endpointMappings, ClassLoader classLoader, List operations, Binding binding, Style portStyle, SOAPConstants soapVersion, Map exceptionMap, SchemaInfoBuilder schemaInfoBuilder, JavaWsdlMappingType mapping, OperationInfo[] operationInfos, String portName, URL location, List handlerInfos, Map seiPortNameToFactoryMap, Map seiClassNameToFactoryMap, String credentialsName) throws DeploymentException {
        Class serviceEndpointInterface;
        SEIFactory seiFactory;
        //complete jaxrpc mapping file supplied
        QName portTypeQName = portType.getQName();
        ServiceEndpointInterfaceMappingType endpointMapping = WSDescriptorParser.getServiceEndpointInterfaceMapping(endpointMappings, portTypeQName);
        String fqcn = endpointMapping.getServiceEndpointInterface().getStringValue();
        try {
            serviceEndpointInterface = classLoader.loadClass(fqcn);
        } catch (ClassNotFoundException e) {
            throw new DeploymentException("Could not load service endpoint interface", e);
        }
//        Class enhancedServiceEndpointClass = enhanceServiceEndpointInterface(serviceEndpointInterface, context, module, classLoader);

        Collection operationDescs = new ArrayList();
        ServiceEndpointMethodMappingType[] methodMappings = endpointMapping.getServiceEndpointMethodMappingArray();
        int i = 0;
        Set wrapperElementQNames = new HashSet();
        JavaXmlTypeMappingType[] javaXmlTypeMappings = mapping.getJavaXmlTypeMappingArray();
        boolean hasEncoded = false;
        for (Iterator ops = operations.iterator(); ops.hasNext();) {
            Operation operation = (Operation) ops.next();
            String operationName = operation.getName();
            //the obvious method seems to be buggy
//            BindingOperation bindingOperation = binding.getBindingOperation(operationName, operation.getInput().getName(), operation.getOutput() == null ? null : operation.getOutput().getName());
            BindingOperation bindingOperation = null;
            List bops = binding.getBindingOperations();
            for (Iterator iterator = bops.iterator(); iterator.hasNext();) {
                BindingOperation bindingOperation1 = (BindingOperation) iterator.next();
                if (bindingOperation1.getOperation().equals(operation)) {
                    bindingOperation = bindingOperation1;
                    break;
                }
            }
            if (bindingOperation == null) {
                throw new DeploymentException("No BindingOperation for operation: " + operationName + ", input: " + operation.getInput().getName() + ", output: " + (operation.getOutput() == null ? "<none>" : operation.getOutput().getName()));
            }
            ServiceEndpointMethodMappingType methodMapping = WSDescriptorParser.getMethodMappingForOperation(operationName, methodMappings);
            HeavyweightOperationDescBuilder operationDescBuilder = new HeavyweightOperationDescBuilder(bindingOperation, mapping, methodMapping, portStyle, exceptionMap, schemaInfoBuilder, javaXmlTypeMappings, classLoader, serviceEndpointInterface);
            OperationInfo operationInfo = operationDescBuilder.buildOperationInfo(soapVersion);
            operationInfos[i++] = operationInfo;
            operationDescs.add(operationInfo.getOperationDesc());
            wrapperElementQNames.addAll(operationDescBuilder.getWrapperElementQNames());
            hasEncoded |= operationDescBuilder.isEncoded();
        }
        HeavyweightTypeInfoBuilder builder = new HeavyweightTypeInfoBuilder(classLoader, schemaInfoBuilder.getSchemaTypeKeyToSchemaTypeMap(), wrapperElementQNames, operationDescs, hasEncoded);
        List typeInfo = builder.buildTypeInfo(mapping);

        seiFactory = createSEIFactory(serviceName, portName, serviceEndpointInterface.getName(), typeInfo, location, operationInfos, handlerInfos, credentialsName);
        seiPortNameToFactoryMap.put(portName, seiFactory);
        seiClassNameToFactoryMap.put(serviceEndpointInterface.getName(), seiFactory);
    }

    private void doLightweightMapping(QName serviceName, PortType portType, JavaWsdlMappingType mapping, ClassLoader classLoader, List operations, Binding binding, Style portStyle, SOAPConstants soapVersion, OperationInfo[] operationInfos, SchemaInfoBuilder schemaInfoBuilder, String portName, URL location, List handlerInfos, Map seiPortNameToFactoryMap, Map seiClassNameToFactoryMap, String credentialsName) throws DeploymentException {
        Class serviceEndpointInterface;
        SEIFactory seiFactory;
        //lightweight jaxrpc mapping supplied
        serviceEndpointInterface = getServiceEndpointInterfaceLightweight(portType, mapping, classLoader);
//        Class enhancedServiceEndpointClass = enhanceServiceEndpointInterface(serviceEndpointInterface, context, module, classLoader);

        int i = 0;
        for (Iterator ops = operations.iterator(); ops.hasNext();) {
            Operation operation = (Operation) ops.next();
            Method method = WSDescriptorParser.getMethodForOperation(serviceEndpointInterface, operation);
            BindingOperation bindingOperation = binding.getBindingOperation(operation.getName(), operation.getInput().getName(), operation.getOutput() == null ? null : operation.getOutput().getName());
            operationInfos[i++] = buildOperationInfoLightweight(method, bindingOperation, portStyle, soapVersion);
        }
        LightweightTypeInfoBuilder builder = new LightweightTypeInfoBuilder(classLoader, schemaInfoBuilder.getSchemaTypeKeyToSchemaTypeMap(), Collections.EMPTY_SET);
        List typeInfo = builder.buildTypeInfo(mapping);

        seiFactory = createSEIFactory(serviceName, portName, serviceEndpointInterface.getName(), typeInfo, location, operationInfos, handlerInfos, credentialsName);
        seiPortNameToFactoryMap.put(portName, seiFactory);
        seiClassNameToFactoryMap.put(serviceEndpointInterface.getName(), seiFactory);
    }

    private Class getServiceEndpointInterfaceLightweight(PortType portType, JavaWsdlMappingType mappings, ClassLoader classLoader) throws DeploymentException {
        QName portTypeQName = portType.getQName();
        String portTypeNamespace = portTypeQName.getNamespaceURI();
        String portTypePackage = WSDescriptorParser.getPackageFromNamespace(portTypeNamespace, mappings);
        StringBuffer shortInterfaceName = new StringBuffer(portTypeQName.getLocalPart());
        shortInterfaceName.setCharAt(0, Character.toUpperCase(shortInterfaceName.charAt(0)));
        //TODO just use one buffer!
        String fqcn = portTypePackage + "." + shortInterfaceName.toString();
        try {
            return classLoader.loadClass(fqcn);
        } catch (ClassNotFoundException e) {
            throw new DeploymentException("Could not load service endpoint interface type", e);
        }
    }


    public SEIFactory createSEIFactory(QName serviceName, String portName, String enhancedServiceEndpointClassName, List typeInfo, URL location, OperationInfo[] operationInfos, List handlerInfoInfos, String credentialsName) throws DeploymentException {
        List handlerInfos = buildHandlerInfosForPort(portName, handlerInfoInfos);
        return new SEIFactoryImpl(serviceName, portName, enhancedServiceEndpointClassName, operationInfos, typeInfo, location, handlerInfos, credentialsName);
    }

    private List buildHandlerInfosForPort(String portName, List handlerInfoInfos) {
        List handlerInfos = new ArrayList();
        for (Iterator iterator = handlerInfoInfos.iterator(); iterator.hasNext();) {
            HandlerInfoInfo handlerInfoInfo = (HandlerInfoInfo) iterator.next();
            Set portNames = handlerInfoInfo.getPortNames();
            if (portNames.isEmpty() || portNames.contains(portName)) {
                HandlerInfo handlerInfo = new HandlerInfo(handlerInfoInfo.getHandlerClass(), handlerInfoInfo.getHandlerConfig(), handlerInfoInfo.getSoapHeaders());
                handlerInfos.add(handlerInfo);

                //TODO what about the soap roles??
            }
        }
        return handlerInfos;
    }

/*
    public Class enhanceServiceEndpointInterface(Class serviceEndpointInterface, DeploymentContext deploymentContext, Module module, ClassLoader classLoader) throws DeploymentException {
        Enhancer enhancer = new Enhancer();
        enhancer.setClassLoader(classLoader);
        enhancer.setSuperclass(GenericServiceEndpointWrapper.class);
        enhancer.setInterfaces(new Class[]{serviceEndpointInterface});
        enhancer.setCallbackFilter(new NoOverrideCallbackFilter(GenericServiceEndpointWrapper.class));
        enhancer.setCallbackTypes(new Class[]{NoOp.class, MethodInterceptor.class});
        enhancer.setUseFactory(false);
        ByteArrayRetrievingGeneratorStrategy strategy = new ByteArrayRetrievingGeneratorStrategy();
        enhancer.setStrategy(strategy);
        enhancer.setUseCache(false);
        Class serviceEndpointClass = enhancer.createClass();

        try {
            module.addClass(serviceEndpointClass.getName(), strategy.getClassBytes(), deploymentContext);
        } catch (IOException e) {
            throw new DeploymentException("Could not write out class bytes", e);
        } catch (URISyntaxException e) {
            throw new DeploymentException("Could not constuct URI for location of enhanced class", e);
        }
        return serviceEndpointClass;
    }
*/

    public OperationInfo buildOperationInfoLightweight(Method method, BindingOperation bindingOperation, Style defaultStyle, SOAPConstants soapVersion) throws DeploymentException {
        LightweightOperationDescBuilder operationDescBuilder = new LightweightOperationDescBuilder(bindingOperation, method);
        return operationDescBuilder.buildOperationInfo(soapVersion);
    }

/*
    private static class ByteArrayRetrievingGeneratorStrategy extends DefaultGeneratorStrategy {

        private byte[] classBytes;

        public byte[] transform(byte[] b) {
            classBytes = b;
            return b;
        }

        public byte[] getClassBytes() {
            return classBytes;
        }
    }
*/


    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoBuilder = GBeanInfoBuilder.createStatic(AxisBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addInterface(WebServiceBuilder.class);

        GBEAN_INFO = infoBuilder.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }

}
