/**
 *
 * Copyright 2006 The Apache Software Foundation
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

package org.apache.geronimo.common.propertyeditor;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.geronimo.gbean.AbstractNameQuery;

/**
 *
 *
 * @version $Rev: 356022 $ $Date: 2005-12-11 12:58:34 -0800 (Sun, 11 Dec 2005) $
 *
 * */
public class AbstractNameQueryEditor extends TextPropertyEditorSupport {

    public Object getValue() {
        try {
            return new AbstractNameQuery(new URI(getAsText()));
        } catch (URISyntaxException e) {
            throw new PropertyEditorException(e);
        }
    }

}
