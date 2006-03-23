/**
 *
 * Copyright 2005 The Apache Software Foundation
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
package org.apache.geronimo.console.apache.jk;

import org.apache.geronimo.console.MultiPagePortlet;
import org.apache.geronimo.console.MultiPageModel;

import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;

/**
 * Portlet that helps you configure Geronimo for Apache 2 with mod_jk
 *
 * @version $Rev: 46019 $ $Date: 2004-09-14 05:56:06 -0400 (Tue, 14 Sep 2004) $
 */
public class ApacheConfigPortlet extends MultiPagePortlet {
    public void init(PortletConfig config) throws PortletException {
        super.init(config);
        addHelper(new IndexHandler(), config);
        addHelper(new ConfigHandler(), config);
        addHelper(new AJPHandler(), config);
        addHelper(new WebAppHandler(), config);
        addHelper(new ResultsHandler(), config);
    }

    protected String getModelJSPVariableName() {
        return "model";
    }

    protected MultiPageModel getModel(PortletRequest request) {
        return new BaseApacheHandler.ApacheModel(request);
    }
}
