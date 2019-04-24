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
import hudson.slaves.NodeProperty;
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

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.* ;

public class ViewConfigDotXmlSEC803Test {

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
        mas.grant(View.CONFIGURE, View.READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        mas.grant(Jenkins.READ)
                .everywhere()
                .to(READER);
        mas.grant(MANAGE, View.CONFIGURE, View.READ, Jenkins.READ)
                .everywhere()
                .to(MANAGER);
        j.jenkins.setAuthorizationStrategy(mas);
    }

    @Test(expected = IOException.class)
    public void verifyFailureWhenInvalidJep200PropertyIsAddedByApi() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        j.jenkins.addView(view);

        TestViewProperty validProperty = new TestViewProperty("hello");
        view.getProperties().add(validProperty);
        Optional<Integer> opt = Optional.fromNullable(123);
        TestViewProperty invalidProperty = new TestViewProperty(opt);

        view.getProperties().add(invalidProperty);
        view.save();
    }

    @Test
    public void verifySuccessWhenValidConfigXmlIsSubmitted() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        TestViewProperty prop = new TestViewProperty("now");
        view.getProperties().add(prop);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_VIEW_XML);

        wc.getPage(req);

        View reloadedView = j.getInstance().getView(view.getViewName());
        TestViewProperty helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnPostConfigXmlWhenChangingItemType() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        TestViewProperty prop = new TestViewProperty("hello");
        view.getProperties().add(prop);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(OTHER_VIEW_TYPE_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should prevent changing item type in config.xml post.", 400, e.getStatusCode());
        }

        View reloadedView = j.getInstance().getView(view.getViewName());
        TestViewProperty helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureWhenInvalidJep200ConfigXmlIsSubmitted() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        TestViewProperty prop = new TestViewProperty("hello");
        view.getProperties().add(prop);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(INVALID_JEP200_VIEW_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("JEP-200 prohibited classes are saved.", 403, e.getStatusCode());
        }

        View reloadedView = j.getInstance().getView(view.getViewName());
        TestViewProperty helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnPostConfigXmlWhenUserLacksConfigure() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        TestViewProperty prop = new TestViewProperty("hello");
        view.getProperties().add(prop);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(READER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_VIEW_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without CONFIGURATOR permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require CONFIGURATOR permission.", 403, e.getStatusCode());
        }

        View reloadedView = j.getInstance().getView(view.getViewName());
        TestViewProperty helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifyFailureOnGetConfigXmlWhenUserLacksRead() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(READER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.GET);
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
        ListView view = new ListView("view1", j.jenkins);
        TestViewProperty prop = new TestViewProperty("hello");
        view.getProperties().add(prop);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(MANAGE_PERMISSION_REQUIRED_VIEW_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without MANAGE permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require MANAGE permission.", 403, e.getStatusCode());
        }

        View reloadedView = j.getInstance().getView(view.getViewName());
        TestViewProperty helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyFailureOnPostConfigXmlWithExistingReadResolveWhenUserLacksManage() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        TestViewProperty helloProperty = new TestViewProperty("hello");
        view.getProperties().add(helloProperty);
        TestViewProperty goodbyeProperty = new TestViewProperty("goodbye");
        view.getProperties().add(goodbyeProperty);
        TestViewPropertyWithPermission permissionProperty = new TestViewPropertyWithPermission();
        view.getProperties().add(permissionProperty);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_VIEW_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure without MANAGE permission.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals("Should require MANAGE permission.", 403, e.getStatusCode());
        }

        View reloadedView = j.getInstance().getView(view.getViewName());
        helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("hello"));
    }

    @Test
    public void verifySuccessOnGetConfigXmlWithReadResolveWhenUserLacksManage() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        TestViewPropertyWithPermission permissionProperty = new TestViewPropertyWithPermission();
        view.getProperties().add(permissionProperty);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.GET);
        req.setAdditionalHeader("Content-Type", "application/xml");
        wc.getPage(req);
    }

    @Test
    public void verifySuccessOnPostConfigXmlWithReadResolveWhenUserHasManage() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(MANAGER);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(MANAGE_PERMISSION_REQUIRED_VIEW_XML);

        wc.getPage(req);

        View reloadedView = j.getInstance().getView(view.getViewName());
        TestViewProperty helloProperty = (TestViewProperty) extractGroupProperty(reloadedView);
        assertThat(helloProperty.getValue(), is("goodbye"));
    }

    @Test
    @Issue("SECURITY-803")
    public void verifyTranslatedPropertyIsSaved() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(TRANSFORMING_PROJECT_XML);
        wc.getPage(req);

        File nodeConfigFile = new File(j.getInstance().getRootDir(), "/config.xml");
        String configFileString = new String(Files.readAllBytes(nodeConfigFile.toPath()));
        assertThat(configFileString, not(containsString("Transforming")));
        assertThat(configFileString, containsString("TransformedViewProperty"));
        assertThat(configFileString, containsString("helloTransformed"));
    }

    private ViewProperty extractGroupProperty(View view) {
        for (ViewProperty property : view.getAllProperties()) {
            if (property instanceof TestViewProperty) {
                return property;
            }
        }
        return null;
    }

    private static class TestViewProperty extends ViewProperty {

        private final Object value;

        @DataBoundConstructor
        public TestViewProperty(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

    }

    // Used in MANAGE_PERMISSION_REQUIRED_VIEW_XML
    private static class TestViewPropertyWithPermission extends ViewProperty {

        @DataBoundConstructor
        public TestViewPropertyWithPermission() {
        }

        public Object readResolve() {
            Jenkins.get().checkPermission(MANAGE);
            return this;
        }

    }

    private static class TransformingViewProperty extends ViewProperty {

        private Object value;

        @DataBoundConstructor
        public TransformingViewProperty(Object value) {
            this.value = value;
        }

        public Object readResolve() {
            return new TransformedViewProperty(value);
        }

    }

    private static class TransformedViewProperty extends ViewProperty  {

        private Object value;

        @DataBoundConstructor
        public TransformedViewProperty(Object value) {
            this.value = value + "Transformed";
        }

        public Object getValue() {
            return value;
        }

    }

    private static final String VALID_VIEW_XML =
            "<listView>\n" +
                    "      <owner class=\"hudson\" reference=\"../../..\"/>\n" +
                    "      <name>view1</name>\n" +
                    "      <filterExecutors>false</filterExecutors>\n" +
                    "      <filterQueue>false</filterQueue>\n" +
                    "      <properties class=\"hudson.model.View$PropertyList\">\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "          <value class=\"string\">hello</value>\n" +
                    "        </hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "      </properties>\n" +
                    "      <jobNames>\n" +
                    "        <comparator class=\"hudson.util.CaseInsensitiveComparator\"/>\n" +
                    "      </jobNames>\n" +
                    "      <jobFilters/>\n" +
                    "      <columns>\n" +
                    "        <hudson.views.StatusColumn/>\n" +
                    "        <hudson.views.WeatherColumn/>\n" +
                    "        <hudson.views.JobColumn/>\n" +
                    "        <hudson.views.LastSuccessColumn/>\n" +
                    "        <hudson.views.LastFailureColumn/>\n" +
                    "        <hudson.views.LastDurationColumn/>\n" +
                    "        <hudson.views.BuildButtonColumn/>\n" +
                    "      </columns>\n" +
                    "      <recurse>false</recurse>\n" +
                    "    </listView>\n";

    private static final String INVALID_JEP200_VIEW_XML =
            "<listView>\n" +
                    "      <owner class=\"hudson\" reference=\"../../..\"/>\n" +
                    "      <name>view1</name>\n" +
                    "      <filterExecutors>false</filterExecutors>\n" +
                    "      <filterQueue>false</filterQueue>\n" +
                    "      <properties class=\"hudson.model.View$PropertyList\">\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "          <value class=\"string\">hello</value>\n" +
                    "        </hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "          <value class=\"com.google.common.base.Optional$Present\">\n" +
                    "            <reference class=\"int\">123</reference>\n" +
                    "          </value>\n" +
                    "        </hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "      </properties>\n" +
                    "      <jobNames>\n" +
                    "        <comparator class=\"hudson.util.CaseInsensitiveComparator\"/>\n" +
                    "      </jobNames>\n" +
                    "      <jobFilters/>\n" +
                    "      <columns>\n" +
                    "        <hudson.views.StatusColumn/>\n" +
                    "        <hudson.views.WeatherColumn/>\n" +
                    "        <hudson.views.JobColumn/>\n" +
                    "        <hudson.views.LastSuccessColumn/>\n" +
                    "        <hudson.views.LastFailureColumn/>\n" +
                    "        <hudson.views.LastDurationColumn/>\n" +
                    "        <hudson.views.BuildButtonColumn/>\n" +
                    "      </columns>\n" +
                    "      <recurse>false</recurse>\n" +
                    "    </listView>\n";

    private static final String MANAGE_PERMISSION_REQUIRED_VIEW_XML =
            "<listView>\n" +
                    "      <owner class=\"hudson\" reference=\"../../..\"/>\n" +
                    "      <name>view1</name>\n" +
                    "      <filterExecutors>false</filterExecutors>\n" +
                    "      <filterQueue>false</filterQueue>\n" +
                    "      <properties class=\"hudson.model.View$PropertyList\">\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "          <value class=\"string\">goodbye</value>\n" +
                    "        </hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test_-TestViewPropertyWithPermission/>\n" +
                    "      </properties>\n" +
                    "      <jobNames>\n" +
                    "        <comparator class=\"hudson.util.CaseInsensitiveComparator\"/>\n" +
                    "      </jobNames>\n" +
                    "      <jobFilters/>\n" +
                    "      <columns>\n" +
                    "        <hudson.views.StatusColumn/>\n" +
                    "        <hudson.views.WeatherColumn/>\n" +
                    "        <hudson.views.JobColumn/>\n" +
                    "        <hudson.views.LastSuccessColumn/>\n" +
                    "        <hudson.views.LastFailureColumn/>\n" +
                    "        <hudson.views.LastDurationColumn/>\n" +
                    "        <hudson.views.BuildButtonColumn/>\n" +
                    "      </columns>\n" +
                    "      <recurse>false</recurse>\n" +
                    "    </listView>\n";

    private static final String OTHER_VIEW_TYPE_XML =
            "<hudson.model.AllView>\n" +
                    "      <owner class=\"hudson\" reference=\"../../..\"/>\n" +
                    "      <name>view1</name>\n" +
                    "      <filterExecutors>false</filterExecutors>\n" +
                    "      <filterQueue>false</filterQueue>\n" +
                    "      <properties class=\"hudson.model.View$PropertyList\">\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "          <value class=\"string\">now</value>\n" +
                    "        </hudson.model.ViewConfigDotXmlSEC803Test_-TestViewProperty>\n" +
                    "      </properties>\n" +
                    "    </hudson.model.AllView>\n";

    private static final String TRANSFORMING_PROJECT_XML =
            "<listView>\n" +
                    "      <owner class=\"hudson\" reference=\"../../..\"/>\n" +
                    "      <name>view1</name>\n" +
                    "      <filterExecutors>false</filterExecutors>\n" +
                    "      <filterQueue>false</filterQueue>\n" +
                    "      <properties class=\"hudson.model.View$PropertyList\">\n" +
                    "        <hudson.model.ViewConfigDotXmlSEC803Test-TransformingViewProperty>\n" +
                    "          <value class=\"string\">hello</value>\n" +
                    "        </hudson.model.ViewConfigDotXmlSEC803Test-TransformingViewProperty>\n" +
                    "      </properties>\n" +
                    "      <jobNames>\n" +
                    "        <comparator class=\"hudson.util.CaseInsensitiveComparator\"/>\n" +
                    "      </jobNames>\n" +
                    "      <jobFilters/>\n" +
                    "      <columns>\n" +
                    "        <hudson.views.StatusColumn/>\n" +
                    "        <hudson.views.WeatherColumn/>\n" +
                    "        <hudson.views.JobColumn/>\n" +
                    "        <hudson.views.LastSuccessColumn/>\n" +
                    "        <hudson.views.LastFailureColumn/>\n" +
                    "        <hudson.views.LastDurationColumn/>\n" +
                    "        <hudson.views.BuildButtonColumn/>\n" +
                    "      </columns>\n" +
                    "      <recurse>false</recurse>\n" +
                    "    </listView>\n";

}
