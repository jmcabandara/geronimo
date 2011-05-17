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
package org.apache.geronimo.security.realm.providers;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.servlet.http.HttpServletRequest;

public class RequestCallbackHandler implements CallbackHandler{

    HttpServletRequest httpRequest;
    
    public RequestCallbackHandler(HttpServletRequest httpRequest){
        this.httpRequest=httpRequest;
    }
    
    public void handle(Callback callbacks[]) throws UnsupportedCallbackException{
        for (int i = 0; i < callbacks.length; i++) {
            Callback callback = callbacks[i];
            if (callback instanceof RequestCallback) {
                RequestCallback rc = (RequestCallback) callback;
                rc.setRequest(httpRequest);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}