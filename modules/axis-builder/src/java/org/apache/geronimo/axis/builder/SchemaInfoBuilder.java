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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;
import javax.wsdl.Definition;
import javax.wsdl.Import;
import javax.wsdl.Types;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.schema.Schema;
import javax.xml.namespace.QName;

import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.schema.SchemaConversionUtils;
import org.apache.xmlbeans.SchemaField;
import org.apache.xmlbeans.SchemaGlobalElement;
import org.apache.xmlbeans.SchemaParticle;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlError;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @version $Rev:  $ $Date:  $
 */
public class SchemaInfoBuilder {
    private static final Log log = LogFactory.getLog(SchemaInfoBuilder.class);
    private static final SchemaTypeSystem basicTypeSystem;

    static {
        URL url = WSDescriptorParser.class.getClassLoader().getResource("soap_encoding_1_1.xsd");
        if (url == null) {
            throw new RuntimeException("Could not locate soap encoding schema");
        }
        Collection errors = new ArrayList();
        XmlOptions xmlOptions = new XmlOptions();
        xmlOptions.setErrorListener(errors);
        try {
            XmlObject xmlObject = SchemaConversionUtils.parse(url);
            basicTypeSystem = XmlBeans.compileXsd(new XmlObject[]{xmlObject}, XmlBeans.getBuiltinTypeSystem(), xmlOptions);
            if (errors.size() > 0) {
                throw new RuntimeException("Could not compile schema type system: errors: " + errors);
            }
        } catch (XmlException e) {
            throw new RuntimeException("Could not compile schema type system", e);
        } catch (IOException e) {
            throw new RuntimeException("Could not compile schema type system", e);
        }
    }

    private final JarFile moduleFile;
    private final Stack uris = new Stack();
    private final Map schemaTypeKeyToSchemaTypeMap;

    public SchemaInfoBuilder(JarFile moduleFile, Definition definition) throws DeploymentException {
        this.moduleFile = moduleFile;
        try {
            URI uri = new URI(definition.getDocumentBaseURI());
            uris.push(uri);
        } catch (URISyntaxException e) {
            throw new DeploymentException("Could not locate definition", e);
        }
        SchemaTypeSystem schemaTypeSystem = compileSchemaTypeSystem(definition);
        schemaTypeKeyToSchemaTypeMap = buildSchemaTypeKeyToSchemaTypeMap(schemaTypeSystem);
    }

    SchemaInfoBuilder(JarFile moduleFile, URI uri, SchemaTypeSystem schemaTypeSystem) {
        this.moduleFile = moduleFile;
        uris.push(uri);
        schemaTypeKeyToSchemaTypeMap = buildSchemaTypeKeyToSchemaTypeMap(schemaTypeSystem);
    }

    public Map getSchemaTypeKeyToSchemaTypeMap() {
        return schemaTypeKeyToSchemaTypeMap;
    }

    private static final String[] errorNames = {
        "Error", "Warning", "Info"
    };

    public SchemaTypeSystem compileSchemaTypeSystem(Definition definition) throws DeploymentException {
        List schemaList = new ArrayList();
        addImportsFromDefinition(definition, schemaList);
//        System.out.println("Schemas: " + schemaList);
        Collection errors = new ArrayList();
        XmlOptions xmlOptions = new XmlOptions();
        xmlOptions.setErrorListener(errors);
        xmlOptions.setEntityResolver(new JarEntityResolver());
        XmlObject[] schemas = (XmlObject[]) schemaList.toArray(new XmlObject[schemaList.size()]);
        try {
            SchemaTypeSystem schemaTypeSystem = XmlBeans.compileXsd(schemas, basicTypeSystem, xmlOptions);
            if (errors.size() > 0) {
                boolean wasError = false;
                for (Iterator iterator = errors.iterator(); iterator.hasNext();) {
                    XmlError xmlError = (XmlError) iterator.next();
                    log.info("Severity: " + errorNames[xmlError.getSeverity()] + ", message: " + xmlError);
                    if (xmlError.getSeverity() == XmlError.SEVERITY_ERROR) {
                        wasError = true;
                    }
                }
                if (wasError) {
                    throw new DeploymentException("Could not compile schema type system, see log for errors");
                }
            }
            return schemaTypeSystem;
        } catch (XmlException e) {
            throw new DeploymentException("Could not compile schema type system", e);
        }
    }

    private void addImportsFromDefinition(Definition definition, List schemaList) throws DeploymentException {
        Map namespaceMap = definition.getNamespaces();
        Types types = definition.getTypes();
        if (types != null) {
            List schemas = types.getExtensibilityElements();
            for (Iterator iterator = schemas.iterator(); iterator.hasNext();) {
                Object o = iterator.next();
                if (o instanceof Schema) {
                    Schema unknownExtensibilityElement = (Schema) o;
                    QName elementType = unknownExtensibilityElement.getElementType();
                    if (new QName("http://www.w3.org/2001/XMLSchema", "schema").equals(elementType)) {
                        Element element = unknownExtensibilityElement.getElement();
                        addSchemaElement(element, namespaceMap, schemaList);
                    }
                } else if (o instanceof UnknownExtensibilityElement) {
                    //This is allegedly obsolete as of axis-wsdl4j-1.2-RC3.jar which includes the Schema extension above.
                    //The change notes imply that imported schemas should end up in Schema elements.  They don't, so this is still needed.
                    UnknownExtensibilityElement unknownExtensibilityElement = (UnknownExtensibilityElement) o;
                    Element element = unknownExtensibilityElement.getElement();
                    String elementNamespace = element.getNamespaceURI();
                    String elementLocalName = element.getNodeName();
                    if ("http://www.w3.org/2001/XMLSchema".equals(elementNamespace) && "schema".equals(elementLocalName)) {
                        addSchemaElement(element, namespaceMap, schemaList);
                    }
                }
            }
        }
        Map imports = definition.getImports();
        if (imports != null) {
            for (Iterator iterator = imports.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String namespaceURI = (String) entry.getKey();
                List importList = (List) entry.getValue();
                for (Iterator iterator1 = importList.iterator(); iterator1.hasNext();) {
                    Import anImport = (Import) iterator1.next();
                    //according to the 1.1 jwsdl mr shcema imports are supposed to show up here,
                    //but according to the 1.0 spec there is supposed to be no Definition.
                    Definition definition1 = anImport.getDefinition();
                    if (definition1 != null) {
                        try {
                            URI uri = new URI(definition1.getDocumentBaseURI());
                            uris.push(uri);
                        } catch (URISyntaxException e) {
                            throw new DeploymentException("Could not locate definition", e);
                        }
                        try {
                            addImportsFromDefinition(definition1, schemaList);
                        } finally {
                            uris.pop();
                        }
                    } else {
                        log.warn("Missing definition in import for namespace " + namespaceURI);
                    }
                }
            }
        }
    }

    private void addSchemaElement(Element element, Map namespaceMap, List schemaList) throws DeploymentException {
        try {
            XmlObject xmlObject = SchemaConversionUtils.parse(element);
            XmlCursor cursor = xmlObject.newCursor();
            try {
                cursor.toFirstContentToken();
                for (Iterator namespaces = namespaceMap.entrySet().iterator(); namespaces.hasNext();) {
                    Map.Entry entry = (Map.Entry) namespaces.next();
                    cursor.insertNamespace((String) entry.getKey(), (String) entry.getValue());
                }
            } finally {
                cursor.dispose();
            }
            schemaList.add(xmlObject);
        } catch (XmlException e) {
            throw new DeploymentException("Could not parse schema element", e);
        }
    }

    /**
     * builds a map of SchemaTypeKey containing jaxrpc-style fake QName and context info to xmlbeans SchemaType object.
     *
     * @param schemaTypeSystem
     * @return
     */
    public Map buildSchemaTypeKeyToSchemaTypeMap(SchemaTypeSystem schemaTypeSystem) {
        Map qnameMap = new HashMap();
        SchemaType[] globalTypes = schemaTypeSystem.globalTypes();
        for (int i = 0; i < globalTypes.length; i++) {
            SchemaType globalType = globalTypes[i];
            QName typeQName = globalType.getName();
            addSchemaType(typeQName, globalType, false, qnameMap);
        }
        SchemaGlobalElement[] globalElements = schemaTypeSystem.globalElements();
        for (int i = 0; i < globalElements.length; i++) {
            SchemaGlobalElement globalElement = globalElements[i];
            addElement(globalElement, null, qnameMap);
        }
        return qnameMap;
    }

    private void addElement(SchemaField element, SchemaTypeKey key, Map qnameMap) {
        //TODO is this null if element is a ref?
        QName elementName = element.getName();
        String elementNamespace = elementName.getNamespaceURI();
        if (elementNamespace == null || elementNamespace.equals("")) {
            elementNamespace = key.getqName().getNamespaceURI();
        }
        String elementQNameLocalName;
        SchemaTypeKey elementKey = null;
        if (key == null) {
            //top level. rule 2.a,
            elementQNameLocalName = elementName.getLocalPart();
            elementKey = new SchemaTypeKey(elementName, true, false, false);
        } else {
            //not top level. rule 2.b, key will be for enclosing Type.
            QName enclosingTypeQName = key.getqName();
            String enclosingTypeLocalName = enclosingTypeQName.getLocalPart();
            elementQNameLocalName = enclosingTypeLocalName + ">" + elementName.getLocalPart();
            QName subElementName = new QName(elementNamespace, elementQNameLocalName);
            elementKey = new SchemaTypeKey(subElementName, true, false, true);
        }
        SchemaType schemaType = element.getType();
        qnameMap.put(elementKey, schemaType);
//        new Exception("Adding: " + elementKey.getqName().getLocalPart()).printStackTrace();
        //check if it's an array. maxOccurs is null if unbounded
        //element should always be a SchemaParticle... this is a workaround for XMLBEANS-137
        if (element instanceof SchemaParticle) {
            addArrayForms((SchemaParticle) element, elementKey.getqName(), qnameMap, schemaType);
        } else {
            log.warn("element is not a schemaParticle! " + element);
        }
        //now, name for type.  Rule 1.b, type inside an element
        String typeQNameLocalPart = ">" + elementQNameLocalName;
        QName typeQName = new QName(elementNamespace, typeQNameLocalPart);
        boolean isAnonymous = true;
        addSchemaType(typeQName, schemaType, isAnonymous, qnameMap);
    }

    private void addSchemaType(QName typeQName, SchemaType schemaType, boolean anonymous, Map qnameMap) {
        SchemaTypeKey typeKey = new SchemaTypeKey(typeQName, false, schemaType.isSimpleType(), anonymous);
        qnameMap.put(typeKey, schemaType);
//        new Exception("Adding: " + typeKey.getqName().getLocalPart()).printStackTrace();
        //TODO xmlbeans recommends using summary info from getElementProperties and getAttributeProperties instead of traversing the content model by hand.
        SchemaParticle schemaParticle = schemaType.getContentModel();
        if (schemaParticle != null) {
            addSchemaParticle(schemaParticle, typeKey, qnameMap);
        }
    }


    private void addSchemaParticle(SchemaParticle schemaParticle, SchemaTypeKey key, Map qnameMap) {
        if (schemaParticle.getParticleType() == SchemaParticle.ELEMENT) {
            SchemaType elementType = schemaParticle.getType();
            SchemaField element = elementType.getContainerField();
            //element will be null if the type is defined elsewhere, such as a built in type.
            if (element != null) {
                addElement(element, key, qnameMap);
            } else {
                QName keyQName = key.getqName();
                //TODO I can't distinguish between 3.a and 3.b, so generate names both ways.
                //3.b
                String localPart = schemaParticle.getName().getLocalPart();
                QName elementName = new QName(keyQName.getNamespaceURI(), localPart);
                addArrayForms(schemaParticle, elementName, qnameMap, elementType);
                //3.a
                localPart = keyQName.getLocalPart() + ">" + schemaParticle.getName().getLocalPart();
                elementName = new QName(keyQName.getNamespaceURI(), localPart);
                addArrayForms(schemaParticle, elementName, qnameMap, elementType);
            }
        } else {
            SchemaParticle[] children = schemaParticle.getParticleChildren();
            for (int i = 0; i < children.length; i++) {
                SchemaParticle child = children[i];
                addSchemaParticle(child, key, qnameMap);
            }
        }
    }

    private void addArrayForms(SchemaParticle schemaParticle, QName keyName, Map qnameMap, SchemaType elementType) {
        //it may be a ref or a built in type.  If it's an array (maxOccurs >1) form a type for it.
        if (schemaParticle.getIntMaxOccurs() > 1) {
            String maxOccurs = schemaParticle.getMaxOccurs() == null ? "unbounded" : "" + schemaParticle.getIntMaxOccurs();
            int minOccurs = schemaParticle.getIntMinOccurs();
            QName elementName = schemaParticle.getName();
            String arrayQNameLocalName = keyName.getLocalPart() + "[" + minOccurs + "," + maxOccurs + "]";
            String elementNamespace = elementName.getNamespaceURI();
            if (elementNamespace == null || elementNamespace.equals("")) {
                elementNamespace = keyName.getNamespaceURI();
            }
            QName arrayName = new QName(elementNamespace, arrayQNameLocalName);
            SchemaTypeKey arrayKey = new SchemaTypeKey(arrayName, false, false, true);
            //TODO not clear we want the schemaType as the value
            qnameMap.put(arrayKey, elementType);
//            new Exception("Adding: " + arrayKey.getqName().getLocalPart()).printStackTrace();
            if (minOccurs == 1) {
                arrayQNameLocalName = keyName.getLocalPart() + "[," + maxOccurs + "]";
                arrayName = new QName(elementNamespace, arrayQNameLocalName);
                arrayKey = new SchemaTypeKey(arrayName, false, false, true);
                //TODO not clear we want the schemaType as the value
                qnameMap.put(arrayKey, elementType);
            }
        }
    }

    /**
     * Find all the complex types in the previously constructed schema analysis.
     * Put them in a map from complex type QName to schema fragment.
     *
     * @return
     */
    public Map getComplexTypesInWsdl() {
        Map complexTypeMap = new HashMap();
        for (Iterator iterator = schemaTypeKeyToSchemaTypeMap.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            SchemaTypeKey key = (SchemaTypeKey) entry.getKey();
            if (!key.isSimpleType() && !key.isAnonymous()) {
                QName qName = key.getqName();
                SchemaType schemaType = (SchemaType) entry.getValue();
                complexTypeMap.put(qName, schemaType);
            }
        }
        return complexTypeMap;
    }

    public Map getElementToTypeMap() {
        Map elementToTypeMap = new HashMap();
        for (Iterator iterator = schemaTypeKeyToSchemaTypeMap.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            SchemaTypeKey key = (SchemaTypeKey) entry.getKey();
            if (key.isElement()) {
                QName elementQName = key.getqName();
                SchemaType schemaType = (SchemaType) entry.getValue();
                QName typeQName = schemaType.getName();
                elementToTypeMap.put(elementQName, typeQName);
            }
        }
        return elementToTypeMap;
    }

    private class JarEntityResolver implements EntityResolver {

        private final static String PROJECT_URL_PREFIX = "project://local/";
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            //seems like this must be a bug in xmlbeans...
            if (systemId.indexOf(PROJECT_URL_PREFIX) > -1) {
                systemId = systemId.substring(PROJECT_URL_PREFIX.length());
            }
            URI location = ((URI) uris.peek()).resolve(systemId);
//            System.out.println("SystemId: " + systemId + ", location: " + location);
            InputStream wsdlInputStream = null;
            try {
                ZipEntry entry = moduleFile.getEntry(location.toString());
//                System.out.println("entry: " + entry.getName());
                wsdlInputStream = moduleFile.getInputStream(entry);
//                byte[] buf = new byte[1024];
//                int i;
//                while ((i = wsdlInputStream.read(buf)) > 0 ) {
//                    System.out.write(buf, 0, i);
//                }
//                wsdlInputStream.close();
//                wsdlInputStream = moduleFile.getInputStream(entry);
            } catch (IOException e) {
                throw new RuntimeException("Could not open stream to wsdl file", e);
            }
            return new InputSource(wsdlInputStream);
        }
    }

}
