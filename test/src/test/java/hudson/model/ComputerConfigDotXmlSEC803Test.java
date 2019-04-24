/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.google.common.base.Optional;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;

public class ComputerConfigDotXmlSEC803Test {

    private static final Permission MANAGE = new Permission(Item.PERMISSIONS, "Manage", Messages._Item_CONFIGURE_description(), Jenkins.ADMINISTER, PermissionScope.ITEM);
    private static final String CONFIGURATOR = "configure_user";
    private static final String READER = "read_user";
    private static final String MANAGER = "manage_user";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setupSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Computer.CONFIGURE, Computer.EXTENDED_READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        mas.grant(Jenkins.READ)
                .everywhere()
                .to(READER);
        mas.grant(MANAGE, Computer.CONFIGURE, Computer.EXTENDED_READ, Jenkins.READ)
                .everywhere()
                .to(MANAGER);
        j.jenkins.setAuthorizationStrategy(mas);
    }

    @Test(expected = RuntimeException.class)
    public void verifyFailureWhenInvalidJep200PropertyIsAddedByApi() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodeProperty validProperty = new TestNodeProperty("hello");
        node.getNodeProperties().add(validProperty);
        Optional<Integer> opt = Optional.fromNullable(123);
        TestNodeProperty invalidProperty = new TestNodeProperty(opt);

        node.getNodeProperties().add(invalidProperty);
    }

    @Test
    public void verifySuccessWhenValidConfigXmlIsSubmitted() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR, CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_NODE_XML);

        wc.getPage(req);

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        TestNodeProperty helloProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnPostConfigXmlWhenChangingItemType() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodeProperty helloProperty = new TestNodeProperty("hello");
        node.getNodeProperties().add(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(OTHER_NODE_TYPE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should prevent changing item type in config.xml post.", 400, e.getStatusCode());
        }

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        helloProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureWhenInvalidJep200ConfigXmlIsSubmitted() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodeProperty helloProperty = new TestNodeProperty("hello");
        node.getNodeProperties().add(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(INVALID_JEP200_NODE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("JEP-200 prohibited classes are saved.", 403, e.getStatusCode());
        }

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        helloProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnPostConfigXmlWhenUserLacksConfigure() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodeProperty helloProperty = new TestNodeProperty("hello");
        node.getNodeProperties().add(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(READER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_NODE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without CONFIGURATOR permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require CONFIGURATOR permission.", 403, e.getStatusCode());
        }

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        helloProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnGetConfigXmlWhenUserLacksExtendedRead() throws Exception {
        Computer computer = j.createSlave().toComputer();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(READER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.GET);
        req.setAdditionalHeader("Content-Type", "application/xml");

        try {
            wc.getPage(req);
            fail("Should have returned failure without EXTENDED_READ permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require EXTENDED_READ permission.", 403, e.getStatusCode());
        }
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureOnPostConfigXmlWithNewReadResolveWhenUserLacksManage() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodeProperty helloProperty = new TestNodeProperty("hello");
        node.getNodeProperties().add(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(MANAGE_PERMISSION_REQUIRED_NODE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without MANAGE permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require MANAGE permission.", 403, e.getStatusCode());
        }

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        helloProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureOnPostConfigXmlWithExistingReadResolveWhenUserLacksManage() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodeProperty helloProperty = new TestNodeProperty("hello");
        node.getNodeProperties().add(helloProperty);
        TestNodeProperty goodbyeProperty = new TestNodeProperty("goodbye");
        node.getNodeProperties().add(goodbyeProperty);
        TestNodePropertyWithPermission permissionProperty = new TestNodePropertyWithPermission();
        node.getNodeProperties().add(permissionProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_NODE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without MANAGE permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require MANAGE permission.", 403, e.getStatusCode());
        }

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        helloProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifySuccessOnGetConfigXmlWithReadResolveWhenUserLacksManage() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();
        TestNodePropertyWithPermission permissionProperty = new TestNodePropertyWithPermission();
        node.getNodeProperties().add(permissionProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.GET);
        req.setAdditionalHeader("Content-Type", "application/xml");
        wc.getPage(req);
    }

    @Test
    public void verifySuccessOnPostConfigXmlWithReadResolveWhenUserHasManage() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(MANAGER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(MANAGE_PERMISSION_REQUIRED_NODE_XML);

        wc.getPage(req);

        Node reloadedNode = j.getInstance().getNode(node.getNodeName());
        TestNodeProperty goodbyeProperty = reloadedNode.getNodeProperty(TestNodeProperty.class);
        assertThat(goodbyeProperty.getValue(), is("goodbye"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyTranslatedPropertyIsSaved() throws Exception {
        Computer computer = j.createSlave().toComputer();
        Node node = computer.getNode();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(TRANSFORMING_PROJECT_XML);
        wc.getPage(req);

        File nodeConfigFile = new File(j.getInstance().getRootDir(), "nodes/" + node.getNodeName() + "/config.xml");
        String configFileString = new String(Files.readAllBytes(nodeConfigFile.toPath()));
        assertThat(configFileString, not(containsString("Transforming")));
        assertThat(configFileString, containsString("TransformedJobProperty"));
        assertThat(configFileString, containsString("helloTransformed"));
    }

    @Test
    public void configXmlGetShouldFailForUnauthorized() throws Exception {
        Computer computer = j.createSlave().toComputer();

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.GET);
        req.setAdditionalHeader("Content-Type", "application/xml");

        try {
            wc.getPage(req);
            fail("Unauthorized should not be able to GET.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Unauthorized should not be able to GET.", 403, e.getStatusCode());
        }
    }

    @Test
    public void configXmlPostShouldFailForUnauthorized() throws Exception {
        Computer computer = j.createSlave().toComputer();

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");

        try {
            wc.getPage(req);
            fail("Unauthorized should not be able to POST.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Unauthorized should not be able to POST.", 403, e.getStatusCode());
        }
    }

    private static class TestNodeProperty extends NodeProperty<Node> {

        private final Object value;

        @DataBoundConstructor
        public TestNodeProperty(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

    }

    // Used for OTHER_NODE_TYPE_XML
    private static class TestNode extends Slave {

        public TestNode(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
            super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        }
    }

    private static class TestNodePropertyWithPermission extends NodeProperty<Node> {

        @DataBoundConstructor
        public TestNodePropertyWithPermission() {
        }

        public Object readResolve() {
            Jenkins.get().checkPermission(MANAGE);
            return this;
        }

    }

    private static class TransformingNodeProperty extends NodeProperty<Node> {

        private Object value;

        @DataBoundConstructor
        public TransformingNodeProperty(Object value) {
            this.value = value;
        }

        public Object readResolve() {
            return new TransformedJobProperty(value);
        }

    }

    private static class TransformedJobProperty extends NodeProperty<Node>  {

        private Object value;

        @DataBoundConstructor
        public TransformedJobProperty(Object value) {
            this.value = value + "Transformed";
        }

        public Object getValue() {
            return value;
        }

    }

    private static final String VALID_NODE_XML =
            "<slave>\n" +
                    "  <name>slave0</name>\n" +
                    "  <description>dummy</description>\n" +
                    "  <remoteFS>/Users/jeffret/src/cert/jenkins/test/target/jenkinsTests.tmp/jenkins2037363906033030403test</remoteFS>\n" +
                    "  <numExecutors>1</numExecutors>\n" +
                    "  <mode>NORMAL</mode>\n" +
                    "  <retentionStrategy class=\"hudson.slaves.RetentionStrategy$2\">\n" +
                    "    <DESCRIPTOR>\n" +
                    "      <outer-class reference=\"../..\"/>\n" +
                    "    </DESCRIPTOR>\n" +
                    "  </retentionStrategy>\n" +
                    "  <launcher class=\"org.jvnet.hudson.test.SimpleCommandLauncher\">\n" +
                    "    <cmd>&quot;/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/bin/java&quot;  -jar &quot;/Users/jeffret/src/cert/jenkins/war/target/jenkins/WEB-INF/lib/remoting-3.17.1.jar&quot;</cmd>\n" +
                    "  </launcher>\n" +
                    "  <label></label>\n" +
                    "  <nodeProperties>\n" +
                    "    <hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "      <value class=\"string\">hello</value>\n" +
                    "    </hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "  </nodeProperties>\n" +
                    "  <userId>SYSTEM</userId>\n" +
                    "</slave>";

    private static final String INVALID_JEP200_NODE_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
                    "<slave>\n" +
                    "  <name>slave0</name>\n" +
                    "  <description>dummy</description>\n" +
                    "  <remoteFS>/Users/jeffret/src/cert/jenkins/test/target/jenkinsTests.tmp/jenkins2037363906033030403test</remoteFS>\n" +
                    "  <numExecutors>1</numExecutors>\n" +
                    "  <mode>NORMAL</mode>\n" +
                    "  <retentionStrategy class=\"hudson.slaves.RetentionStrategy$2\">\n" +
                    "    <DESCRIPTOR>\n" +
                    "      <outer-class reference=\"../..\"/>\n" +
                    "    </DESCRIPTOR>\n" +
                    "  </retentionStrategy>\n" +
                    "  <launcher class=\"org.jvnet.hudson.test.SimpleCommandLauncher\">\n" +
                    "    <cmd>&quot;/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/bin/java&quot;  -jar &quot;/Users/jeffret/src/cert/jenkins/war/target/jenkins/WEB-INF/lib/remoting-3.17.1.jar&quot;</cmd>\n" +
                    "  </launcher>\n" +
                    "  <label></label>\n" +
                    "  <nodeProperties>\n" +
                    "    <hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "      <value class=\"string\">hello</value>\n" +
                    "    </hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "    <hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "      <value class=\"com.google.common.base.Optional$Present\">\n" +
                    "        <reference class=\"int\">123</reference>\n" +
                    "      </value>\n" +
                    "    </hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "  </nodeProperties>\n" +
                    "  <userId>SYSTEM</userId>\n" +
                    "</slave>";

    private static final String MANAGE_PERMISSION_REQUIRED_NODE_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
                    "<slave>\n" +
                    "  <name>slave0</name>\n" +
                    "  <description>dummy</description>\n" +
                    "  <remoteFS>/Users/jeffret/src/cert/jenkins/test/target/jenkinsTests.tmp/jenkins2037363906033030403test</remoteFS>\n" +
                    "  <numExecutors>1</numExecutors>\n" +
                    "  <mode>NORMAL</mode>\n" +
                    "  <retentionStrategy class=\"hudson.slaves.RetentionStrategy$2\">\n" +
                    "    <DESCRIPTOR>\n" +
                    "      <outer-class reference=\"../..\"/>\n" +
                    "    </DESCRIPTOR>\n" +
                    "  </retentionStrategy>\n" +
                    "  <launcher class=\"org.jvnet.hudson.test.SimpleCommandLauncher\">\n" +
                    "    <cmd>&quot;/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/bin/java&quot;  -jar &quot;/Users/jeffret/src/cert/jenkins/war/target/jenkins/WEB-INF/lib/remoting-3.17.1.jar&quot;</cmd>\n" +
                    "  </launcher>\n" +
                    "  <label></label>\n" +
                    "  <nodeProperties>\n" +
                    "    <hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodePropertyWithPermission/>\n" +
                    "    <hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "      <value class=\"string\">goodbye</value>\n" +
                    "    </hudson.model.ComputerConfigDotXmlSEC803Test_-TestNodeProperty>\n" +
                    "  </nodeProperties>\n" +
                    "  <userId>SYSTEM</userId>\n" +
                    "</slave>";

    private static final String OTHER_NODE_TYPE_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
                    "<hudson.model.ComputerConfigDotXmlSEC803Test-TestNode>\n" +
                    "  <keepDependencies>false</keepDependencies>\n" +
                    "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                    "  <canRoam>false</canRoam>\n" +
                    "  <disabled>false</disabled>\n" +
                    "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>\n" +
                    "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>\n" +
                    "  <triggers/>\n" +
                    "  <concurrentBuild>false</concurrentBuild>\n" +
                    "  <builders/>\n" +
                    "  <publishers/>\n" +
                    "  <buildWrappers/>\n" +
                    "</hudson.model.ComputerConfigDotXmlSEC803Test-TestNode>";

    private static final String TRANSFORMING_PROJECT_XML =
            "<slave>\n" +
                    "  <name>slave0</name>\n" +
                    "  <description>dummy</description>\n" +
                    "  <remoteFS>/Users/jeffret/src/cert/jenkins/test/target/jenkinsTests.tmp/jenkins2037363906033030403test</remoteFS>\n" +
                    "  <numExecutors>1</numExecutors>\n" +
                    "  <mode>NORMAL</mode>\n" +
                    "  <retentionStrategy class=\"hudson.slaves.RetentionStrategy$2\">\n" +
                    "    <DESCRIPTOR>\n" +
                    "      <outer-class reference=\"../..\"/>\n" +
                    "    </DESCRIPTOR>\n" +
                    "  </retentionStrategy>\n" +
                    "  <launcher class=\"org.jvnet.hudson.test.SimpleCommandLauncher\">\n" +
                    "    <cmd>&quot;/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home/jre/bin/java&quot;  -jar &quot;/Users/jeffret/src/cert/jenkins/war/target/jenkins/WEB-INF/lib/remoting-3.17.1.jar&quot;</cmd>\n" +
                    "  </launcher>\n" +
                    "  <label></label>\n" +
                    "  <nodeProperties>\n" +
                    "    <hudson.model.ComputerConfigDotXmlSEC803Test_-TransformingNodeProperty>\n" +
                    "      <value class=\"string\">hello</value>\n" +
                    "    </hudson.model.ComputerConfigDotXmlSEC803Test_-TransformingNodeProperty>\n" +
                    "  </nodeProperties>\n" +
                    "  <userId>SYSTEM</userId>\n" +
                    "</slave>";

}
