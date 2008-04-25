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
package org.apache.geronimo.console.keystores;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.geronimo.console.MultiPageModel;
import org.apache.geronimo.management.geronimo.KeystoreException;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import java.io.IOException;

/**
 * Handler for entering a password to allow editing of a keystore
 *
 * @version $Rev$ $Date$
 */
public class EditKeystoreHandler extends BaseKeystoreHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());
    public EditKeystoreHandler() {
        super(UNLOCK_KEYSTORE_FOR_EDITING, "/WEB-INF/view/keystore/unlockKeystore.jsp");
    }

    public String actionBeforeView(ActionRequest request, ActionResponse response, MultiPageModel model) throws PortletException, IOException {
        String keystore = request.getParameter("keystore");
        if(keystore != null) {
            response.setRenderParameter("keystore", keystore);
        } // else we hope this is after a failure and the actionAfterView took care of it below!
        return getMode();
    }

    public void renderView(RenderRequest request, RenderResponse response, MultiPageModel model) throws PortletException, IOException {
        String[] params = {ERROR_MSG, INFO_MSG};
        for(int i = 0; i < params.length; ++i) {
            String value = request.getParameter(params[i]);
            if(value != null) request.setAttribute(params[i], value);
        }
        request.setAttribute("keystore", request.getParameter("keystore"));
        request.setAttribute("mode", "unlockEdit");
    }

    public String actionAfterView(ActionRequest request, ActionResponse response, MultiPageModel model) throws PortletException, IOException {
        String keystore = request.getParameter("keystore");
        String password = request.getParameter("password");
        if(keystore == null || keystore.equals("")) {
            return getMode(); // todo: this is bad; if there's no ID, then the form on the page is just not valid!
        } else if(password == null) {
            response.setRenderParameter("keystore", keystore);
            return getMode();
        }
        KeystoreData data = ((KeystoreData) request.getPortletSession(true).getAttribute(KEYSTORE_DATA_PREFIX + keystore));
        char[] storePass = password.toCharArray();
        try {
            data.unlockEdit(storePass);
        } catch (KeystoreException e) {
            response.setRenderParameter(ERROR_MSG, "Unable to unlock keystore "+keystore+" for editing. "+e.toString());
            log.error("Unable to unlock keystore "+keystore+" for editing.", e);
            return getMode()+BEFORE_ACTION;
        }
        response.setRenderParameter(INFO_MSG, "Keystore "+keystore+" successfully unlocked for editing.");
        return LIST_MODE+BEFORE_ACTION;
    }
}
