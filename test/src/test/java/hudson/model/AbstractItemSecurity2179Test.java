/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.CheckForNull;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

public class AbstractItemSecurity2179Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-2179")
    public void testQueueRouteDisabled() throws Exception {
        FreeStyleProject job = j.createProject(FreeStyleProject.class, "job");
        job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
        Assert.assertEquals(1, Jenkins.get().getQueue().getItems().length);

        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = webClient.goTo("queue/items/0/task/");
        Assert.assertThat(htmlPage.getWebResponse().getStatusCode(), is(404));
    }

    @Test
    @Issue("SECURITY-2179")
    public void testExecutorRouteDisabled() throws Exception {
        j.jenkins.setNumExecutors(1);
        FreeStyleProject job = j.createProject(FreeStyleProject.class, "job");
        job.getBuildersList().add(new SleepBuilder(100000));
        job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart();
        Assert.assertEquals(1, Jenkins.get().getQueue().getLeftItems().size());

        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = webClient.goTo("computer/(master)/executors/0/currentExecutable/");
        Assert.assertThat(htmlPage.getWebResponse().getStatusCode(), is(404));
    }

    @Test
    @Issue("SECURITY-2179")
    public void testCustomAction() throws Exception {
        final MockFolder folder = j.createFolder("foo");
        folder.createProject(FreeStyleProject.class, "bar");

        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = webClient.goTo("router/item/");
        Assert.assertThat(htmlPage.getWebResponse().getStatusCode(), is(404));
    }

    @TestExtension
    public static class AlternativeRouteAction implements RootAction {

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "router";
        }

        public Item getItem() {
            return Jenkins.get().getItemByFullName("foo/bar");
        }
    }
}
