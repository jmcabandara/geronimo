package org.apache.geronimo.client.builder;

import java.io.File;
import java.util.Collections;

import org.apache.geronimo.testsupport.TestSupport;

import org.apache.geronimo.xbeans.geronimo.client.GerApplicationClientDocument;
import org.apache.geronimo.xbeans.geronimo.client.GerApplicationClientType;
import org.apache.geronimo.xbeans.geronimo.naming.GerResourceRefType;
import org.apache.geronimo.kernel.repository.Environment;
import org.apache.geronimo.deployment.xbeans.EnvironmentType;
import org.apache.geronimo.deployment.xbeans.ArtifactType;
import org.apache.geronimo.deployment.xmlbeans.XmlBeansUtil;

/**
 */
public class PlanParsingTest extends TestSupport {

    private AppClientModuleBuilder builder;

    protected void setUp() throws Exception {
        builder = new AppClientModuleBuilder(new Environment(), null, null, null, null, Collections.EMPTY_LIST, null, null, null, null);
    }

    public void testResourceRef() throws Exception {
        File resourcePlan = new File(BASEDIR, "src/test/resources/plans/plan1.xml");
        assertTrue(resourcePlan.exists());
        GerApplicationClientType appClient = builder.getGeronimoAppClient(resourcePlan, null, true, null, null, null);
        assertEquals(1, appClient.getResourceRefArray().length);
    }

    public void testConstructPlan() throws Exception {
        GerApplicationClientDocument appClientDoc = GerApplicationClientDocument.Factory.newInstance();
        GerApplicationClientType appClient = appClientDoc.addNewApplicationClient();
        EnvironmentType clientEnvironmentType = appClient.addNewClientEnvironment();
        ArtifactType clientId = clientEnvironmentType.addNewModuleId();
        clientId.setGroupId("group");
        clientId.setArtifactId("artifact");
        EnvironmentType serverEnvironmentType = appClient.addNewServerEnvironment();
        serverEnvironmentType.setModuleId(clientId);

        GerResourceRefType ref = appClient.addNewResourceRef();
        ref.setRefName("ref");
        ref.setResourceLink("target");

        XmlBeansUtil.validateDD(appClient);
        // System.out.println(appClient.toString());
    }

    public void testConnectorInclude() throws Exception {
        File resourcePlan = new File(BASEDIR, "src/test/resources/plans/plan2.xml");
        assertTrue(resourcePlan.exists());
        GerApplicationClientType appClient = builder.getGeronimoAppClient(resourcePlan, null, true, null, null, null);
        assertEquals(1, appClient.getResourceRefArray().length);
        assertEquals(1, appClient.getResourceArray().length);
    }
}
