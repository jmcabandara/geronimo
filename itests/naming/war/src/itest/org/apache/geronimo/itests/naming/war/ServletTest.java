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
package org.apache.geronimo.itests.naming.war;

import junit.framework.TestCase;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * @version $Rev:  $ $Date:  $
 */
public class ServletTest extends TestCase {

    public void testWebService() throws Exception {
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod("http://localhost:8080/geronimo/itests/naming/NamingTestServlet");
        httpClient.executeMethod(getMethod);
        String response = getMethod.getResponseBodyAsString();
//        System.out.println(response);
        if (response != null && !response.equals("")) {
            fail("Received output " + response);
        }
    }

}
