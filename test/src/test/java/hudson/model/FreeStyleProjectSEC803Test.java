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
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class FreeStyleProjectSEC803Test {

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
        mas.grant(Item.CONFIGURE, Item.READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        mas.grant(Item.READ, Jenkins.READ)
                .everywhere()
                .to(READER);
        mas.grant(MANAGE, Item.CONFIGURE, Item.READ, Jenkins.READ)
                .everywhere()
                .to(MANAGER);
        j.jenkins.setAuthorizationStrategy(mas);
    }

    @Test(expected = IOException.class)
    public void verifyFailureWhenInvalidJep200PropertyIsAddedByApi() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobProperty validProperty = new TestJobProperty("hello");
        project.addProperty(validProperty);
        Optional<Integer> opt = Optional.fromNullable(123);
        TestJobProperty invalidProperty = new TestJobProperty(opt);

        project.addProperty(invalidProperty);
    }

    @Test
    public void verifySuccessWhenValidConfigXmlIsSubmitted() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_PROJECT_XML);

        wc.getPage(req);

        project.doReload();
        TestJobProperty helloProperty = project.getProperty(TestJobProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnPostConfigXmlWhenChangingItemType() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobProperty helloProperty = new TestJobProperty("hello");
        project.addProperty(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(OTHER_PROJECT_TYPE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should prevent changing item type in config.xml post.", 400, e.getStatusCode());
        }

        project.doReload();
        helloProperty = project.getProperty(TestJobProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureWhenInvalidJep200ConfigXmlIsSubmitted() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobProperty helloProperty = new TestJobProperty("hello");
        project.addProperty(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR, CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(INVALID_JEP200_PROJECT_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("JEP-200 prohibited classes are saved.", 403, e.getStatusCode());
        }

        project.doReload();
        helloProperty = project.getProperty(TestJobProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnPostConfigXmlWhenUserLacksExtendedRead() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobProperty helloProperty = new TestJobProperty("hello");
        project.addProperty(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(READER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_PROJECT_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without EXTENDED_READ permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require EXTENDED_READ permission.", 403, e.getStatusCode());
        }

        project.doReload();
        helloProperty = project.getProperty(TestJobProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnGetConfigXmlWhenUserLacksConfigure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(READER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.GET);
        req.setAdditionalHeader("Content-Type", "application/xml");

        try {
            wc.getPage(req);
            fail("Should have returned failure without CONFIGURATOR permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require CONFIGURATOR permission.", 403, e.getStatusCode());
        }
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureOnPostConfigXmlWithNewReadResolveWhenUserLacksManage() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobProperty helloProperty = new TestJobProperty("hello");
        project.addProperty(helloProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(MANAGE_PERMISSION_REQUIRED_PROJECT_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without MANAGE permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require MANAGE permission.", 403, e.getStatusCode());
        }

        project.doReload();
        helloProperty = project.getProperty(TestJobProperty.class);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureOnPostConfigXmlWithExistingReadResolveWhenUserLacksManage() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobProperty goodbyeProperty = new TestJobProperty("goodbye");
        project.addProperty(goodbyeProperty);
        TestJobPropertyWithPermission permissionProperty = new TestJobPropertyWithPermission();
        project.addProperty(permissionProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_PROJECT_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without MANAGE permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require MANAGE permission.", 403, e.getStatusCode());
        }

        project.doReload();
        goodbyeProperty = project.getProperty(TestJobProperty.class);
        assertThat(goodbyeProperty.getValue(), is("goodbye"));
    }

    @Test
    public void verifySuccessOnGetConfigXmlWithReadResolveWhenUserLacksManage() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestJobPropertyWithPermission permissionProperty = new TestJobPropertyWithPermission();
        project.addProperty(permissionProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.GET);
        req.setAdditionalHeader("Content-Type", "application/xml");
        wc.getPage(req);
    }

    @Test
    public void verifySuccessOnPostConfigXmlWithReadResolveWhenUserHasManage() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(MANAGER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(MANAGE_PERMISSION_REQUIRED_PROJECT_XML);

        wc.getPage(req);

        project.doReload();
        TestJobProperty goodbyeProperty = project.getProperty(TestJobProperty.class);
        assertThat(goodbyeProperty.getValue(), is("goodbye"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyTranslatedPropertyIsSaved() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", project.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(TRANSFORMING_PROJECT_XML);
        wc.getPage(req);

        String configFileString = project.getConfigFile().asString();
        assertThat(configFileString, not(containsString("Transforming")));
        assertThat(configFileString, containsString("TransformedJobProperty"));
        assertThat(configFileString, containsString("helloTransformed"));
    }

    private static class TestJobProperty extends JobProperty<FreeStyleProject> {

        private final Object value;

        @DataBoundConstructor
        public TestJobProperty(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

    }

    private static class TestFreeStyleProject extends FreeStyleProject {

        public TestFreeStyleProject(ItemGroup parent, String name) {
            super(parent, name);
        }

        @TestExtension
        public static class DescriptorImpl extends FreeStyleProject.DescriptorImpl {

            @Override
            public FreeStyleProject newInstance(ItemGroup parent, String name) {
                return new TestFreeStyleProject(parent, name);
            }

        }
    }

    // Used in MANAGE_PERMISSION_REQUIRED_PROJECT_XML
    private static class TestJobPropertyWithPermission extends JobProperty<FreeStyleProject> {

        @DataBoundConstructor
        public TestJobPropertyWithPermission() {
        }

        public Object readResolve() {
            Jenkins.get().checkPermission(MANAGE);
            return this;
        }

    }

    private static class TransformingJobProperty extends JobProperty<FreeStyleProject> {

        private Object value;

        @DataBoundConstructor
        public TransformingJobProperty(Object value) {
            this.value = value;
        }

        public Object readResolve() {
            return new TransformedJobProperty(value);
        }

    }

    private static class TransformedJobProperty extends JobProperty<FreeStyleProject> {

        private Object value;

        @DataBoundConstructor
        public TransformedJobProperty(Object value) {
            this.value = value + "Transformed";
        }

        public Object getValue() {
            return value;
        }

    }

    private static final String VALID_PROJECT_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
                    "<project>\n" +
                    "  <keepDependencies>false</keepDependencies>\n" +
                    "  <properties>\n" +
                    "    <hudson.model.FreeStyleProjectSEC803Test_-TestJobProperty>\n" +
                    "      <value class=\"string\">hello</value>\n" +
                    "    </hudson.model.FreeStyleProjectSEC803Test_-TestJobProperty>\n" +
                    "  </properties>\n" +
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
                    "</project>";

    private static final String INVALID_JEP200_PROJECT_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
            "<project>\n" +
                    "  <keepDependencies>false</keepDependencies>\n" +
                    "  <properties>\n" +
                    "    <hudson.model.FreeStyleProjectSEC803Test-TestJobProperty>\n" +
                    "      <value class=\"com.google.common.base.Optional$Present\">\n" +
                    "        <reference class=\"int\">123</reference>\n" +
                    "      </value>\n" +
                    "    </hudson.model.FreeStyleProjectSEC803Test-TestJobProperty>\n" +
                    "    <hudson.model.FreeStyleProjectSEC803Test_-TestJobProperty>\n" +
                    "      <value class=\"string\">goodbye</value>\n" +
                    "    </hudson.model.FreeStyleProjectSEC803Test_-TestJobProperty>\n" +
                    "  </properties>\n" +
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
                    "</project>";

    private static final String MANAGE_PERMISSION_REQUIRED_PROJECT_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
            "<project>\n" +
                    "  <keepDependencies>false</keepDependencies>\n" +
                    "  <properties>\n" +
                    "    <hudson.model.FreeStyleProjectSEC803Test-TestJobPropertyWithPermission/>\n"+
                    "    <hudson.model.FreeStyleProjectSEC803Test_-TestJobProperty>\n" +
                    "      <value class=\"string\">goodbye</value>\n" +
                    "    </hudson.model.FreeStyleProjectSEC803Test_-TestJobProperty>\n" +
                    "  </properties>\n" +
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
                    "</project>";

    private static final String OTHER_PROJECT_TYPE_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
                    "<hudson.model.FreeStyleProjectSEC803Test_-TestFreeStyleProject>\n" +
                    "  <keepDependencies>false</keepDependencies>\n" +
                    "  <properties/>\n" +
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
                    "</hudson.model.FreeStyleProjectSEC803Test_-TestFreeStyleProject>";

    private static final String TRANSFORMING_PROJECT_XML =
            "<?xml version='1.1' encoding='UTF-8'?>\n" +
                    "<project>\n" +
                    "  <keepDependencies>false</keepDependencies>\n" +
                    "  <properties>\n" +
                    "    <hudson.model.FreeStyleProjectSEC803Test_-TransformingJobProperty>\n" +
                    "      <value class=\"string\">hello</value>\n" +
                    "    </hudson.model.FreeStyleProjectSEC803Test_-TransformingJobProperty>\n" +
                    "  </properties>\n" +
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
                    "</project>";

}
