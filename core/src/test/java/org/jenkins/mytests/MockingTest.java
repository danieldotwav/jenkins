package org.jenkins.mytests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.pages.SignupPage;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class MockingTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test // 2.5 Covers: Delete User -> Confirm Deletion
    public void deleteUser2() throws Exception {
        // Set up the real security realm
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // First, sign up a new user
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("deleteTest2");
        signup.enterPassword("12345");
        signup.enterFullName("Delete TestUser2");

        // Submit the sign-up form
        HtmlPage success = signup.submit(j);

        // Verify that the user was created successfully by checking the main-panel and the user's display name
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertEquals("Delete TestUser2", securityRealm.getUser("deleteTest2").getDisplayName());

        // Now, get the user to delete
        User userToDelete = securityRealm.getUser("deleteTest2");

        // Create a spy on the actual user to verify method calls
        User spiedUser = Mockito.spy(userToDelete);

        // Perform deletion using the spied user
        spiedUser.delete();

        // Verify that the delete method was called on the spied user
        Mockito.verify(spiedUser).delete();

        // Now verify that the user has been deleted in the security realm
        assertNull(securityRealm.getUser("deleteTest2")); // Assert that the user no longer exists in the security realm
        assertEquals(0, securityRealm.getAllUsers().size()); // Assert that the list of users is now empty
    }
}