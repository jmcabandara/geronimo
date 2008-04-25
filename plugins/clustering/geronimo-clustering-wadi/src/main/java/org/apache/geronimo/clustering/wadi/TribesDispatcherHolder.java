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
package org.apache.geronimo.clustering.wadi;

import java.net.URI;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.geronimo.clustering.LocalNode;
import org.apache.geronimo.clustering.Node;
import org.apache.geronimo.gbean.GBeanLifecycle;
import org.apache.geronimo.gbean.annotation.ParamAttribute;
import org.apache.geronimo.gbean.annotation.ParamReference;
import org.apache.geronimo.gbean.annotation.ParamSpecial;
import org.apache.geronimo.gbean.annotation.SpecialAttributeType;
import org.codehaus.wadi.core.reflect.base.DeclaredMemberFilter;
import org.codehaus.wadi.core.reflect.jdk.JDKClassIndexerRegistry;
import org.codehaus.wadi.core.util.SimpleStreamer;
import org.codehaus.wadi.group.Dispatcher;
import org.codehaus.wadi.group.DispatcherRegistry;
import org.codehaus.wadi.group.MessageExchangeException;
import org.codehaus.wadi.group.StaticDispatcherRegistry;
import org.codehaus.wadi.servicespace.admin.AdminServiceSpace;
import org.codehaus.wadi.tribes.TribesDispatcher;
import org.codehaus.wadi.web.impl.URIEndPoint;

/**
 *
 * @version $Rev$ $Date$
 */
public class TribesDispatcherHolder implements GBeanLifecycle, DispatcherHolder {
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final URI endPointURI;
    private final String clusterName;
    private final LocalNode node;
    private final ClassLoader cl;
    private final DispatcherRegistry dispatcherRegistry;

    private TribesDispatcher dispatcher;
    private AdminServiceSpace adminServiceSpace;

    public TribesDispatcherHolder(@ParamSpecial(type=SpecialAttributeType.classLoader) ClassLoader cl,
        @ParamAttribute(name=GBEAN_ATTR_END_POINT_URI) URI endPointURI,
        @ParamAttribute(name=GBEAN_ATTR_CLUSTER_NAME) String clusterName,
        @ParamReference(name=GBEAN_REF_NODE) LocalNode node) {
        if (null == endPointURI) {
            throw new IllegalArgumentException("endPointURI is required");
        } else if (null == clusterName) {
            throw new IllegalArgumentException("clusterName is required");
        } else if (null == node) {
            throw new IllegalArgumentException("node is required");
        } else if (null == cl) {
            throw new IllegalArgumentException("cl is required");
        }
        this.endPointURI = endPointURI;
        this.clusterName = clusterName;
        this.node = node;
        this.cl = cl;
        
        dispatcherRegistry = new StaticDispatcherRegistry();
    }

    public void doStart() throws Exception {
        dispatcher = new TribesDispatcher(clusterName,
            node.getName(),
            new URIEndPoint(endPointURI),
            Collections.EMPTY_SET);
        dispatcher.start();
        
        adminServiceSpace = new AdminServiceSpace(dispatcher,
            new JDKClassIndexerRegistry(new DeclaredMemberFilter()),
            new SimpleStreamer(cl));
        
        registerCustomAdminServices();
        
        adminServiceSpace.start();
        
        dispatcherRegistry.register(dispatcher);
    }

    public void doStop() throws Exception {
        adminServiceSpace.stop();
        dispatcherRegistry.unregister(dispatcher);
        dispatcher.stop();
    }

    public void doFail() {
        if (null != adminServiceSpace) {
            try {
                adminServiceSpace.stop();
            } catch (Exception e) {
                log.error("see nested", e);
            }
        }
        
        if (null != dispatcher) {
            dispatcherRegistry.unregister(dispatcher);
            try {
                dispatcher.stop();
            } catch (MessageExchangeException e) {
                log.error("see nested", e);
            }
        }
    }
    
    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public Node getNode() {
        return node;
    }
    
    protected void registerCustomAdminServices() {
        NodeServiceHelper nodeServiceHelper = new NodeServiceHelper(adminServiceSpace);
        nodeServiceHelper.registerNodeService(new BasicNodeService(node));
    }
    
    public static final String GBEAN_ATTR_END_POINT_URI = "endPointURI";
    public static final String GBEAN_ATTR_CLUSTER_NAME = "clusterName";
    public static final String GBEAN_ATTR_CLUSTER_URI = "clusterUri";
    public static final String GBEAN_REF_NODE = "Node";
}
