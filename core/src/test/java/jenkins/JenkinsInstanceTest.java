package jenkins;

import static org.junit.Assert.assertNotNull;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class JenkinsInstanceTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testJenkinsInstanceIsNotNull() {
        Jenkins jenkins = Jenkins.get();
        assertNotNull("The Jenkins instance should not be null", jenkins);
    }
}
