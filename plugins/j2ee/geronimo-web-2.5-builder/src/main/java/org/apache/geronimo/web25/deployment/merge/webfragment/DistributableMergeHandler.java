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

package org.apache.geronimo.web25.deployment.merge.webfragment;

import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.web25.deployment.merge.MergeContext;
import org.apache.openejb.jee.Empty;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.jee.WebFragment;

/**
 * @version $Rev$ $Date$
 */
public class DistributableMergeHandler implements WebFragmentMergeHandler<WebFragment, WebApp> {

    public static final String APPLICATION_DISTRIBUTABLE_VALUE = "distributable";

    @Override
    public void merge(WebFragment webFragment, WebApp webApp, MergeContext mergeContext) throws DeploymentException {
        boolean currentMergedDistributableValue = (Boolean) mergeContext.getAttribute(APPLICATION_DISTRIBUTABLE_VALUE);
        if (currentMergedDistributableValue) {
            mergeContext.setAttribute(APPLICATION_DISTRIBUTABLE_VALUE, webFragment.getDistributable().size() > 0);
        }
    }

    @Override
    public void postProcessWebXmlElement(WebApp webApp, MergeContext mergeContext) throws DeploymentException {
        boolean currentMergedDistributableValue = (Boolean) mergeContext.getAttribute(APPLICATION_DISTRIBUTABLE_VALUE);
        boolean distributableInWebXml = webApp.getDistributable().size() > 0;
        if (currentMergedDistributableValue) {
            if (!distributableInWebXml) {
                webApp.getDistributable().add(new Empty());
            }
        } else {
            if (distributableInWebXml) {
                for (int i = 0, iLoopSize = webApp.getDistributable().size(); i < iLoopSize; i++) {
                    webApp.getDistributable().clear();
                }
            }
        }
    }

    @Override
    public void preProcessWebXmlElement(WebApp webApp, MergeContext context) throws DeploymentException {
        context.setAttribute(CURRENT_MERGED_DISTRIBUTABLE_VALUE, webApp.getDistributable().size() > 0);
    }
}
