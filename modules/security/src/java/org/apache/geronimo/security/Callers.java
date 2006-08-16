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

package org.apache.geronimo.security;

import javax.security.auth.Subject;

/**
 * @version $Rev:$ $Date:$
 */
public class Callers {

    private final Subject currentCaller;
    private final Subject nextCaller;

    public Callers(Subject currentCaller, Subject nextCaller) {
        this.currentCaller = currentCaller;
        this.nextCaller = nextCaller;
    }

    public Subject getCurrentCaller() {
        return currentCaller;
    }

    public Subject getNextCaller() {
        return nextCaller;
    }
}
