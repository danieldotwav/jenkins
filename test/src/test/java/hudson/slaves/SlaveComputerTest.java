/*
 * The MIT License
 *
 * Copyright (c) 2018, suren <zxjlwt@126.com>
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

package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import hudson.model.*;
import hudson.remoting.Launcher;
import hudson.security.ACL;
import hudson.security.ACLContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOError;
import java.io.IOException;

import hudson.util.StreamTaskListener;
import hudson.util.io.RewindableRotatingFileOutputStream;
import jenkins.model.Jenkins;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.htmlunit.WebResponse;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

/**
 * @author suren
 */
public class SlaveComputerTest {
    // --------------- Existing Implementation ---------------
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAgentLogs() throws Exception {
        DumbSlave node = j.createOnlineSlave();
        String log = node.getComputer().getLog();
        assertTrue(log.contains("Remoting version: " + Launcher.VERSION));
        assertTrue(log.contains("Launcher: " + SimpleCommandLauncher.class.getSimpleName()));
        assertTrue(log.contains("Communication Protocol: Standard in/out"));
        assertTrue(log.contains(String.format("This is a %s agent", Functions.isWindows() ? "Windows" : "Unix")));
    }

    @Test
    public void testGetAbsoluteRemotePath() throws Exception {
        //default auth
        DumbSlave nodeA = j.createOnlineSlave();
        String path = nodeA.getComputer().getAbsoluteRemotePath();
        Assert.assertNotNull(path);
        Assert.assertEquals(getRemoteFS(nodeA, null), path);

        //not auth
        String userAlice = "alice";
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Computer.CONFIGURE, Jenkins.READ).everywhere().to(userAlice);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(authStrategy);
        try (ACLContext context = ACL.as(User.getById(userAlice, true))) {
            path = nodeA.getComputer().getAbsoluteRemotePath();
            Assert.assertNull(path);
            Assert.assertNull(getRemoteFS(nodeA, userAlice));
        }

        //with auth
        String userBob = "bob";
        authStrategy.grant(Computer.CONNECT, Jenkins.READ).everywhere().to(userBob);
        try (ACLContext context = ACL.as(User.getById(userBob, true))) {
            path = nodeA.getComputer().getAbsoluteRemotePath();
            Assert.assertNotNull(path);
            Assert.assertNotNull(getRemoteFS(nodeA, userBob));
        }
    }

    @Test
    @Issue("JENKINS-57111")
    public void startupShouldNotFailOnExceptionOnlineListener() throws Exception {
        DumbSlave nodeA = j.createOnlineSlave();
        assertThat(nodeA.getComputer(), instanceOf(SlaveComputer.class));

        int retries = 10;
        while (IOExceptionOnOnlineListener.onOnlineCount == 0 && retries > 0) {
            retries--;
            Thread.sleep(500);
        }
        assertTrue(retries > 0);
        Thread.sleep(500);

        Assert.assertFalse(nodeA.getComputer().isOffline());
        assertTrue(nodeA.getComputer().isOnline());

        // Both listeners should fire and not cause the other not to fire.
        Assert.assertEquals(1, IOExceptionOnOnlineListener.onOnlineCount);
        Assert.assertEquals(1, RuntimeExceptionOnOnlineListener.onOnlineCount);

        // We should get the stack trace too.
        assertThat(nodeA.getComputer().getLog(), allOf(
                containsString("\tat " + IOExceptionOnOnlineListener.class.getName() + ".onOnline"),
                containsString("\tat " + RuntimeExceptionOnOnlineListener.class.getName() + ".onOnline")));
    }

    @TestExtension(value = "startupShouldNotFailOnExceptionOnlineListener")
    public static final class IOExceptionOnOnlineListener extends ComputerListener {

        static int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException {
            if (c instanceof SlaveComputer) {
                onOnlineCount++;
                throw new IOException("Something happened (the listener always throws this exception)");
            }
        }
    }

    @TestExtension(value = "startupShouldNotFailOnExceptionOnlineListener")
    public static final class RuntimeExceptionOnOnlineListener extends ComputerListener {

        static int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) {
            if (c instanceof SlaveComputer) {
                onOnlineCount++;
                throw new RuntimeException("Something happened (the listener always throws this exception)");
            }
        }
    }

    @Test
    @Issue("JENKINS-57111")
    public void startupShouldFailOnErrorOnlineListener() throws Exception {
        assumeFalse("TODO: Windows container agents do not have enough resources to run this test", Functions.isWindows() && System.getenv("CI") != null);
        DumbSlave nodeA = j.createSlave();
        assertThat(nodeA.getComputer(), instanceOf(SlaveComputer.class));
        int retries = 10;
        while (ErrorOnOnlineListener.onOnlineCount == 0 && retries > 0) {
            retries--;
            Thread.sleep(500);
        }
        assertTrue(retries > 0);
        Thread.sleep(500);

        Assert.assertEquals(1, ErrorOnOnlineListener.onOnlineCount);

        assertTrue(nodeA.getComputer().isOffline());
        Assert.assertFalse(nodeA.getComputer().isOnline());
    }

    @TestExtension(value = "startupShouldFailOnErrorOnlineListener")
    public static final class ErrorOnOnlineListener extends ComputerListener {

        static volatile int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) {
            if (c instanceof SlaveComputer) {
                onOnlineCount++;
                throw new IOError(new Exception("Something happened (the listener always throws this exception)"));
            }
        }
    }

    private String getRemoteFS(Node node, String user) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        if (user != null) {
            wc.login(user);
        }

        WebResponse response = wc.goTo("computer/" + node.getNodeName() + "/api/json",
                "application/json").getWebResponse();
        JSONObject json = JSONObject.fromObject(response.getContentAsString());

        Object pathObj = json.get("absoluteRemotePath");
        if (pathObj instanceof JSONNull) {
            return null; // the value is null in here
        } else {
            return pathObj.toString();
        }
    }

    // --------------- New Implementations: A simple dummy slave that extends Slave ---------------

    private static class DummySlave extends Slave {
        private final File logFile;

        public DummySlave(String name, File logFile) throws Descriptor.FormException, IOException, Descriptor.FormException {
            // Use a dummy remote FS and a simple JNLPLauncher.
            super(name, "dummyRemoteFS", new JNLPLauncher());
            this.logFile = logFile;
        }
    }

    // Test that the dependency injection constructor of SlaveComputer is invoked and the embedded self-test is executed.
    @Test
    public void testDependencyInjectionConstructorSelfTest() throws Exception {
        // Create a temporary file to serve as the log file.
        File tempLogFile = File.createTempFile("dummy", ".log");
        tempLogFile.deleteOnExit();

        // Create an instance of our dummy slave.
        DummySlave dummySlave = new DummySlave("dummySlave", tempLogFile);

        // Create a dummy log stream based on the temporary file.
        RewindableRotatingFileOutputStream dummyLog =
                new RewindableRotatingFileOutputStream(tempLogFile, 10);

        // Create a ByteArrayOutputStream to capture the task listener's output
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TaskListener dummyListener = new StreamTaskListener(baos);

        // Set the system property so that the self-test code is triggered
        System.setProperty("jenkins.selftest", "true");

        // Invoke the dependency injection constructor
        SlaveComputer sc = new SlaveComputer(dummySlave, dummyLog, dummyListener);

        // Clear the property to avoid side effects
        System.clearProperty("jenkins.selftest");

        // Convert the captured output to a string
        String output = baos.toString("UTF-8");

        // Verify that the self-test message was written to the TaskListener
        assertThat("Self-test message should be present", output, containsString("Self-test log message"));
    }
}
