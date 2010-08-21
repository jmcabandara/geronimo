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

package org.apache.geronimo.datasource;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import org.apache.geronimo.connector.outbound.GenericConnectionManager;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.NoPool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.NoTransactions;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.SinglePool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.TransactionSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.XATransactions;
import org.apache.geronimo.connector.outbound.connectiontracking.ConnectionTracker;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.annotation.GBean;
import org.apache.geronimo.gbean.annotation.OsgiService;
import org.apache.geronimo.gbean.annotation.ParamAttribute;
import org.apache.geronimo.gbean.annotation.ParamReference;
import org.apache.geronimo.gbean.annotation.ParamSpecial;
import org.apache.geronimo.gbean.annotation.SpecialAttributeType;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.naming.ResourceSource;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;
import org.apache.xbean.recipe.ObjectRecipe;
import org.apache.xbean.recipe.Option;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tranql.connector.CredentialExtractor;
import org.tranql.connector.NoExceptionsAreFatalSorter;
import org.tranql.connector.jdbc.AbstractLocalDataSourceMCF;
import org.tranql.connector.jdbc.AbstractPooledConnectionDataSourceMCF;
import org.tranql.connector.jdbc.AbstractXADataSourceMCF;

/**
 * @version $Revision$
 */
@GBean
@OsgiService
public class DataSourceGBean implements ResourceSource<ResourceException>, ServiceFactory {
    
    private static final Logger log = LoggerFactory.getLogger(DataSourceGBean.class);
        
    private final DataSourceDescription dataSourceDescription;
    private final transient ClassLoader classLoader;
    private final transient GenericConnectionManager connectionManager;

    public DataSourceGBean(@ParamAttribute(name="dataSourceDescription") DataSourceDescription dataSourceDescription,
                           @ParamAttribute(name="defaultMaxSize") int defaultMaxSize,
                           @ParamAttribute(name="defaultMinSize") int defaultMinSize,
                           @ParamAttribute(name="defaultBlockingTimeoutMilliseconds") int defaultBlockingTimeoutMilliseconds,
                           @ParamAttribute(name="defaultIdleTimeoutMinutes") int defaultIdleTimeoutMinutes,
                           @ParamAttribute(name="defaultXATransactionCaching") boolean defaultXATransactionCaching,
                           @ParamAttribute(name="defaultXAThreadCaching") boolean defaultXAThreadCaching,
                           @ParamReference(name="ConnectionTracker", namingType = NameFactory.JCA_CONNECTION_TRACKER) ConnectionTracker connectionTracker,
                           @ParamReference(name="TransactionManager", namingType = NameFactory.JTA_RESOURCE) RecoverableTransactionManager transactionManager,
                           @ParamSpecial(type=SpecialAttributeType.objectName) String objectName,
                           @ParamSpecial(type=SpecialAttributeType.abstractName) AbstractName abstractName,
                           @ParamSpecial(type=SpecialAttributeType.classLoader) ClassLoader classLoader,
                           @ParamSpecial(type=SpecialAttributeType.bundleContext) BundleContext bundleContext)
        throws Exception {
        this.dataSourceDescription = dataSourceDescription;        
        this.classLoader = classLoader;
        
        String dsName = dataSourceDescription.getName();
        String dsClass = dataSourceDescription.getClassName();
        
        TransactionSupport transactionSupport;
        PoolingSupport pooling;
        ManagedConnectionFactory mcf;
       
        Object instance = createDataSource();
        
        if (instance instanceof XADataSource) {
            mcf = new XADataSourceMCF((XADataSource) instance);
            if (dataSourceDescription.isTransactional()) {
                transactionSupport = new XATransactions(defaultXATransactionCaching, defaultXAThreadCaching);
            } else {
                transactionSupport = NoTransactions.INSTANCE;
            }
            pooling = createPool(defaultMinSize, 
                                 defaultMaxSize, 
                                 defaultBlockingTimeoutMilliseconds, 
                                 defaultIdleTimeoutMinutes);
        } else if (instance instanceof ConnectionPoolDataSource) {
            mcf = new PooledConnectionDataSourceMCF((ConnectionPoolDataSource) instance);
            if (dataSourceDescription.isTransactional()) {
                log.warn("[{}] Transactional property is true but DataSource does not support transactions", dsName); 
            }
            transactionSupport = NoTransactions.INSTANCE;
            pooling = createPool(defaultMinSize, 
                                 defaultMaxSize, 
                                 defaultBlockingTimeoutMilliseconds, 
                                 defaultIdleTimeoutMinutes);
        } else if (instance instanceof DataSource) {
            mcf = new LocalDataSourceMCF((DataSource) instance, true);
            if (dataSourceDescription.isTransactional()) {
                log.warn("[{}] Transactional property is true but DataSource does not support transactions", dsName); 
            }
            transactionSupport = NoTransactions.INSTANCE;
            if (dataSourceDescription.hasPoolingProperties()) {
                log.warn("[{}] Some pooling properties are set but DataSource does not support pooling", dsName);
            }
            pooling = new NoPool();
        } else {            
            throw new Exception("[" + dsName + "] Invalid DataSource type: " + dsClass);
        }
        
        this.connectionManager = 
            new GenericConnectionManager(transactionSupport, pooling, null, connectionTracker, transactionManager, mcf, objectName, classLoader);
                
        connectionManager.doRecovery();
    }
    
    @Override
    public Object getService(Bundle bundle, ServiceRegistration serviceRegistration) {
        try {
            return connectionManager.createConnectionFactory();
        } catch (ResourceException e) {
            throw new ServiceException("Error creating connection factory", e);
        }
    }

    @Override
    public void ungetService(Bundle bundle, ServiceRegistration serviceRegistration, Object o) {
    }

    private Object createDataSource() throws Exception {
        String className = dataSourceDescription.getClassName();
        Class clazz = classLoader.loadClass(className);
            
        Map<String, Object> properties = new HashMap<String, Object>();
            
        // standard settings
        setProperty(properties, "description", dataSourceDescription.getDescription());
        setProperty(properties, "user", dataSourceDescription.getUser());
        setProperty(properties, "password", dataSourceDescription.getPassword());
        setProperty(properties, "databaseName", dataSourceDescription.getDatabaseName());
        setProperty(properties, "serverName", dataSourceDescription.getServerName());
        if (dataSourceDescription.getPortNumber() != -1) {
            setProperty(properties, "portNumber", dataSourceDescription.getPortNumber());
        }        
        setProperty(properties, "loginTimeout", dataSourceDescription.getLoginTimeout());
        
        /*
         * XXX: Not really sure how deal with url property.
         *      We can't really parse it so pass it as a property to data source.
         */
        
        // set url property if no specific properties are set
        if (dataSourceDescription.hasStandardProperties()) {
            setProperty(properties, "url", dataSourceDescription.getUrl());
        }
        
        // other properties
        if (dataSourceDescription.getProperties() != null) {
            properties.putAll(dataSourceDescription.getProperties());
        }
                                                    
        ObjectRecipe recipe = new ObjectRecipe(clazz, properties);
        recipe.allow(Option.IGNORE_MISSING_PROPERTIES);
            
        Object instance = recipe.create();
            
        Map<String, Object> unset = recipe.getUnsetProperties();
        if (unset != null && !unset.isEmpty()) {
            log.warn("Some DataSource properties were not set {}", unset);
        }
            
        return instance;
    }
    
    private void setProperty(Map<String, Object> properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }

    private PoolingSupport createPool(int defaultMinSize, 
                                      int defaultMaxSize, 
                                      int defaultBlockingTimeoutMilliseconds, 
                                      int defaultIdleTimeoutMinutes) {
        return new SinglePool(
                dataSourceDescription.getMaxPoolSize() != -1 ? dataSourceDescription.getMaxPoolSize() : defaultMaxSize,
                dataSourceDescription.getMinPoolSize() != -1 ? dataSourceDescription.getMinPoolSize() : defaultMinSize,
                defaultBlockingTimeoutMilliseconds,
                dataSourceDescription.getMaxIdleTime() != -1 ? dataSourceDescription.getMaxIdleTime() : defaultIdleTimeoutMinutes,
                false,
                false,
                true);
    }
    
    public Object $getResource() throws ResourceException {
        return connectionManager.createConnectionFactory();
    }
    
    private class PooledConnectionDataSourceMCF extends AbstractPooledConnectionDataSourceMCF {
        
        public PooledConnectionDataSourceMCF(ConnectionPoolDataSource ds) {
            super(ds, new NoExceptionsAreFatalSorter());
        }

        @Override
        public String getPassword() {
            return dataSourceDescription.getPassword();
        }

        @Override
        public String getUserName() {
            return dataSourceDescription.getUser();
        }
    }
    
    private class XADataSourceMCF extends AbstractXADataSourceMCF {
        
        public XADataSourceMCF(XADataSource ds) {
            super(ds, new NoExceptionsAreFatalSorter());
        }

        @Override
        public String getPassword() {
            return dataSourceDescription.getPassword();
        }

        @Override
        public String getUserName() {
            return dataSourceDescription.getUser();
        }
        
        @Override
        protected XAConnection getPhysicalConnection(Subject subject, CredentialExtractor credentialExtractor) 
            throws ResourceException {
            XAConnection connection = super.getPhysicalConnection(subject, credentialExtractor);
            int isolationLevel = dataSourceDescription.getIsolationLevel();
            if (isolationLevel != -1) {
                try {
                    connection.getConnection().setTransactionIsolation(isolationLevel);
                } catch (SQLException e) {
                    throw new ResourceException("Error setting transaction isolation level for ", dataSourceDescription.getName());
                }
            }
            return connection;
        }
    }
    
    private class LocalDataSourceMCF extends AbstractLocalDataSourceMCF {
        
        public LocalDataSourceMCF(DataSource ds, boolean commitBeforeAutocommit) {
            super(ds, new NoExceptionsAreFatalSorter(), commitBeforeAutocommit);
        }

        @Override
        public String getPassword() {
            return dataSourceDescription.getPassword();
        }

        @Override
        public String getUserName() {
            return dataSourceDescription.getUser();
        }
    }

}