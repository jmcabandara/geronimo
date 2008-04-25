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
package org.apache.geronimo.console.configcreator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.geronimo.console.MultiPageModel;

/**
 * A handler for ...
 * 
 * @version $Rev$ $Date$
 */
public class DeployStatusHandler extends AbstractHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public DeployStatusHandler() {
        super(DEPLOY_STATUS_MODE, "/WEB-INF/view/configcreator/deployStatus.jsp");
    }

    public String actionBeforeView(ActionRequest request, ActionResponse response, MultiPageModel model)
            throws PortletException, IOException {
        return getMode();
    }

    public void renderView(RenderRequest request, RenderResponse response, MultiPageModel model)
            throws PortletException, IOException {
        WARConfigData data = getSessionData(request);
        try {
            File moduleFile = new File(new URI(data.getUploadedWarUri()));

            File planFile = File.createTempFile("console-deployment", ".xml");
            planFile.deleteOnExit();
            FileWriter out = new FileWriter(planFile);
            out.write(data.getDeploymentPlan());
            out.close();

            String[] status = JSR88_Util.deploy(request, moduleFile, planFile);
            request.setAttribute(DEPLOY_ABBR_STATUS_PARAMETER, status[0]);
            request.setAttribute(DEPLOY_FULL_STATUS_PARAMETER, status[1]);
        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
        request.setAttribute(DATA_PARAMETER, data);
    }

    public String actionAfterView(ActionRequest request, ActionResponse response, MultiPageModel model)
            throws PortletException, IOException {
        return "";
    }
}
