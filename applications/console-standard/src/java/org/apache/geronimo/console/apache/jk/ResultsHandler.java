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

import org.apache.geronimo.console.MultiPageModel;
import org.apache.geronimo.console.util.PortletManager;
import org.apache.geronimo.kernel.config.ConfigurationInfo;
import org.apache.geronimo.kernel.config.ConfigurationModuleType;
import org.apache.geronimo.management.geronimo.WebManager;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import java.io.IOException;
import java.util.List;

/**
 * Handler for the screen where you select the webapps to expose through Apache
 *
 * @version $Rev: 46019 $ $Date: 2004-09-14 05:56:06 -0400 (Tue, 14 Sep 2004) $
 */
public class ResultsHandler extends BaseApacheHandler {
    public ResultsHandler() {
        super(RESULTS_MODE, "/WEB-INF/view/apache/jk/results.jsp");
    }

    public String actionBeforeView(ActionRequest request, ActionResponse response, MultiPageModel model) throws PortletException, IOException {
        //todo: Add AJP Connector
        return getMode();
    }

    public void renderView(RenderRequest request, RenderResponse response, MultiPageModel amodel) throws PortletException, IOException {
        ApacheModel model = (ApacheModel) amodel;
        String port = "unknown";
        if(model.getAddAjpPort() != null) {
            port = model.getAddAjpPort().toString();
        } else {
            WebManager[] managers = PortletManager.getWebManagers(request);
            // See if any AJP listeners are defined
            for (int i = 0; i < managers.length; i++) {
                WebManager manager = managers[i];
                String[] connectors = manager.getConnectors(WebManager.PROTOCOL_AJP);
                if(connectors.length > 0) {
                    port = Integer.toString(PortletManager.getWebConnector(request, connectors[0]).getPort());
                    break;
                }
            }
        }
        request.setAttribute("ajpPort", port);
    }

    public String actionAfterView(ActionRequest request, ActionResponse response, MultiPageModel model) throws PortletException, IOException {
        return getMode()+BEFORE_ACTION;
    }
}
