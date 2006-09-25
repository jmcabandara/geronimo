/**
 *
 * Copyright 2004 The Apache Software Foundation
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
package org.apache.geronimo.j2ee.deployment;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.jar.JarFile;

import org.apache.geronimo.deployment.DeploymentContext;
import org.apache.geronimo.deployment.DeployableModule;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.kernel.config.ConfigurationModuleType;
import org.apache.geronimo.kernel.repository.Environment;
import org.apache.xmlbeans.XmlObject;

/**
 * @version $Rev$ $Date$
 */
public class AppClientModule extends Module {
    private final Environment clientEnvironment;
    private DeployableModule ear;
    private final AbstractName appClientName;
    private final Collection resourceModules;

    public AppClientModule(boolean standAlone, AbstractName moduleName, AbstractName appClientName, Environment serverEnvironment, Environment clientEnvironment, DeployableModule deployableModule, String targetPath, XmlObject specDD, XmlObject vendorDD, String originalSpecDD, Collection resourceModules) {
        super(standAlone, moduleName, serverEnvironment, moduleFile, targetPath, specDD, vendorDD, originalSpecDD, null);
        this.clientEnvironment = clientEnvironment;
        this.appClientName = appClientName;
        this.resourceModules = resourceModules;
    }

    public ConfigurationModuleType getType() {
        return ConfigurationModuleType.CAR;
    }

    public Environment getClientEnvironment() {
        return clientEnvironment;
    }

    public DeployableModule getEar() {
        return ear;
    }

    public void setEar(DeployableModule ear) {
        this.ear = ear;
    }

    public AbstractName getAppClientName() {
        return appClientName;
    }

    public void addClass(URI location, String fqcn, byte[] bytes, DeploymentContext context) throws IOException, URISyntaxException {
        context.addClass(location, fqcn, bytes);
    }

    public Collection getResourceModules() {
        return resourceModules;
    }

    public void close() {
        if (resourceModules != null) {
            for (Iterator iterator = resourceModules.iterator(); iterator.hasNext();) {
                ConnectorModule connectorModule = (ConnectorModule) iterator.next();
                connectorModule.close();
            }
        }
        super.close();
    }

}


