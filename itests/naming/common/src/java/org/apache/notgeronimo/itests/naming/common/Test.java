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
package org.apache.notgeronimo.itests.naming.common;

import javax.naming.InitialContext;

import org.apache.notgeronimo.itests.naming.common.webservice.interop.InteropLab;
import org.apache.notgeronimo.itests.naming.common.webservice.interop.InteropTestPortType;

/**
 * @version $Rev:  $ $Date:  $
 */
public class Test {

    public void testWebServiceLookup() throws Exception {
        InteropLab interopLab = (InteropLab) new InitialContext().lookup("java:comp/env/service/InteropLab");
        InteropTestPortType interopTestPortType = interopLab.getinteropTestPort();
        int result = interopTestPortType.echoInteger(1);
        if (result != 1) {
            throw new Exception("Result was not 1 but " + result);
        }
    }
}
