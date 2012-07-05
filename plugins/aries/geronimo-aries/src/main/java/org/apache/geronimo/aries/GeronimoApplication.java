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
package org.apache.geronimo.aries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.aries.application.ApplicationMetadata;
import org.apache.aries.application.ApplicationMetadataFactory;
import org.apache.aries.application.DeploymentMetadata;
import org.apache.aries.application.DeploymentMetadataFactory;
import org.apache.aries.application.management.AriesApplication;
import org.apache.aries.application.management.BundleInfo;
import org.apache.aries.application.utils.AppConstants;
import org.apache.geronimo.kernel.util.IOUtils;
import org.apache.aries.application.utils.management.SimpleBundleInfo;
import org.apache.aries.application.utils.manifest.BundleManifest;
import org.apache.geronimo.kernel.repository.UnpackArtifactTypeHandler;
import org.apache.geronimo.kernel.util.FileUtils;
import org.apache.xbean.osgi.bundle.util.BundleUtils;
import org.osgi.framework.Bundle;

/**
 * @version $Rev:385232 $ $Date$
 */
public class GeronimoApplication implements AriesApplication {
    
    private final ApplicationMetadata applicationMetadata;
    private final Set<BundleInfo> bundleInfo;
    private DeploymentMetadata deploymentMetadata;
    
    public GeronimoApplication(Bundle bundle, 
                               ApplicationMetadataFactory applicationFactory, 
                               DeploymentMetadataFactory deploymentFactory) 
        throws IOException {
        
        URL applicationMF = bundle.getEntry(AppConstants.APPLICATION_MF);
        InputStream applicationMFStream = null;
        try {
            applicationMFStream = applicationMF.openStream();
            applicationMetadata = applicationFactory.parseApplicationMetadata(applicationMFStream);
        } finally {
            IOUtils.close(applicationMFStream);
        }

        bundleInfo = new HashSet<BundleInfo>();

        File bundleFile = BundleUtils.toFile(bundle);
        if (bundleFile != null && bundleFile.isDirectory()) {
            collectFileSystemBasedBundleInfos(bundleFile, applicationFactory);
        } else {
            collectBundleEntryBasedBundleInfos(bundle, applicationFactory);
        }

        URL deploymentMF = bundle.getEntry(AppConstants.DEPLOYMENT_MF);
        if (deploymentMF != null) {
            InputStream deploymentMFStream = null;
            try {
                deploymentMFStream = deploymentMF.openStream();
                deploymentMetadata = deploymentFactory.createDeploymentMetadata(deploymentMFStream);
            } finally {
                IOUtils.close(deploymentMFStream);
            }
        }
    }

    public ApplicationMetadata getApplicationMetadata() {
        return applicationMetadata;
    }

    public Set<BundleInfo> getBundleInfo() {
        return bundleInfo;
    }

    public DeploymentMetadata getDeploymentMetadata() {
        return deploymentMetadata;
    }

    public boolean isResolved() {
        return (deploymentMetadata != null);
    }

    public void store(File arg0) throws FileNotFoundException, IOException {
        throw new UnsupportedOperationException();
    }

    public void store(OutputStream arg0) throws FileNotFoundException, IOException {
        throw new UnsupportedOperationException();        
    }
   
    private void collectFileSystemBasedBundleInfos(File baseDir, ApplicationMetadataFactory applicationFactory) throws IOException {
        for (File file : baseDir.listFiles()) {
            if (file.isDirectory() && !file.getCanonicalPath().endsWith(".jar")) {
                collectFileSystemBasedBundleInfos(file, applicationFactory);
                continue;
            }
            BundleManifest bm = BundleManifest.fromBundle(file);
            
            if (bm != null && bm.isValid()) {
                /*
                 * Pass file:// url instead of reference:file:// as bundle location to make sure
                 * Equinox has its own copy of the jar. That is, to prevent strange ZipErrors when
                 * application bundles are updated at runtime, specifically, when 
                 * ApplicationGBean.hotSwapApplicationContent() is called.
                 */
                bundleInfo.add(new SimpleBundleInfo(applicationFactory, bm, "reference:" + file.toURI().toString()));       
                
            }
        }
    }

    private void collectBundleEntryBasedBundleInfos(Bundle bundle, ApplicationMetadataFactory applicationFactory) throws IOException {
        Enumeration<URL> e = bundle.findEntries("/", "*", true);
        while (e.hasMoreElements()) {
            URL url = e.nextElement();
            if (url.getPath().endsWith("/")) {
                continue;
            }
            BundleManifest bm = BundleManifest.fromBundle(url.openStream());
            if (bm != null && bm.isValid()) {
                bundleInfo.add(new SimpleBundleInfo(applicationFactory, bm, url.toExternalForm()));
            }
        }
    }
    
    private void unpackBundleFile(InputStream source, File target) throws IOException{
    	
        ZipInputStream in = new ZipInputStream(source);
        try {

            int threshold = 10240;
            byte[] buffer = new byte[10240];

            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                File file = new File(target, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    if (!entry.getName().equals("META-INF/startup-jar")) {
                        file.getParentFile().mkdirs();
                        OutputStream out = new FileOutputStream(file);
                        try {
                            int count;
                            while ((count = in.read(buffer)) > 0) {
                                out.write(buffer, 0, count);
                                
                                }
                            } finally {
                            IOUtils.flush(out);
                            out.close();
                        }
                        in.closeEntry();
                    }
                }
        }}
            
        catch (IOException e) {
            FileUtils.recursiveDelete(target);
            throw e;
        } finally {
            try {
				in.close();
			} catch (IOException e) {
				//Ignore
			}           
        }
    }
}