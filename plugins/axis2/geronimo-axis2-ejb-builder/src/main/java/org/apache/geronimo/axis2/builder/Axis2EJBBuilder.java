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

package org.apache.geronimo.axis2.builder;

import java.util.Collection;

import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.j2ee.deployment.WebServiceBuilder;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.jaxws.builder.EJBWebServiceFinder;
import org.apache.geronimo.jaxws.wsdl.WsdlGenerator;
import org.apache.geronimo.kernel.repository.Environment;

/**
 * @version $Rev$ $Date$
 */
public class Axis2EJBBuilder extends Axis2Builder {
        
    public Axis2EJBBuilder(Environment defaultEnviroment,
                           Collection<WsdlGenerator> wsdlGenerators) {
        super(defaultEnviroment, wsdlGenerators);
        this.webServiceFinder = new EJBWebServiceFinder();
    }
    
    public Axis2EJBBuilder(){
        super();
    }
              
    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoBuilder = GBeanInfoBuilder.createStatic(Axis2EJBBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addInterface(WebServiceBuilder.class);
        infoBuilder.addAttribute("defaultEnvironment", Environment.class, true, true);
        infoBuilder.addReference("WsdlGenerator", WsdlGenerator.class, GBeanInfoBuilder.DEFAULT_J2EE_TYPE);
        infoBuilder.setConstructor(new String[]{"defaultEnvironment", "WsdlGenerator"});
        GBEAN_INFO = infoBuilder.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }
}