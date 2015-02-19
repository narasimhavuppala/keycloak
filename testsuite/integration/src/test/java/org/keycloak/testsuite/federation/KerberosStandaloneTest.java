package org.keycloak.testsuite.federation;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.keycloak.federation.kerberos.CommonKerberosConfig;
import org.keycloak.federation.kerberos.KerberosConfig;
import org.keycloak.federation.kerberos.KerberosFederationProviderFactory;
import org.keycloak.models.KerberosConstants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserFederationProviderModel;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.rule.KerberosRule;
import org.keycloak.testsuite.rule.KeycloakRule;
import org.keycloak.testsuite.rule.WebRule;

/**
 * Test of KerberosFederationProvider (Kerberos not backed by LDAP)
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class KerberosStandaloneTest extends AbstractKerberosTest {

    public static final String CONFIG_LOCATION = "kerberos/kerberos-standalone-connection.properties";

    private static UserFederationProviderModel kerberosModel;

    private static KerberosRule kerberosRule = new KerberosRule(CONFIG_LOCATION);

    private static KeycloakRule keycloakRule = new KeycloakRule(new KeycloakRule.KeycloakSetup() {

        @Override
        public void config(RealmManager manager, RealmModel adminstrationRealm, RealmModel appRealm) {
            Map<String,String> kerberosConfig = kerberosRule.getConfig();

            kerberosModel = appRealm.addUserFederationProvider(KerberosFederationProviderFactory.PROVIDER_NAME, kerberosConfig, 0, "kerberos-standalone", -1, -1, 0);
            appRealm.addRequiredCredential(UserCredentialModel.KERBEROS);
        }
    });



    @ClassRule
    public static TestRule chain = RuleChain
            .outerRule(kerberosRule)
            .around(keycloakRule);

    @Rule
    public WebRule webRule = new WebRule(this);

    @Rule
    public AssertEvents events = new AssertEvents(keycloakRule);


    @Override
    protected CommonKerberosConfig getKerberosConfig() {
        return new KerberosConfig(kerberosModel);
    }

    @Override
    protected KeycloakRule getKeycloakRule() {
        return keycloakRule;
    }

    @Override
    protected AssertEvents getAssertEvents() {
        return events;
    }


    @Test
    public void spnegoLoginTest() throws Exception {
        spnegoLoginTestImpl();

        // Assert user was imported and hasn't any required action on him
        assertUser("hnelson", "hnelson@keycloak.org", null, null, false);
    }


    @Test
    public void updateProfileEnabledTest() throws Exception {
        // Switch updateProfileOnFirstLogin to on
        KeycloakSession session = keycloakRule.startSession();
        try {
            RealmModel realm = session.realms().getRealm("test");
            UserFederationProviderModel kerberosProviderModel = realm.getUserFederationProviders().get(0);
            kerberosProviderModel.getConfig().put(KerberosConstants.UPDATE_PROFILE_FIRST_LOGIN, "true");
            realm.updateUserFederationProvider(kerberosProviderModel);
        } finally {
            keycloakRule.stopSession(session, true);
        }

        // Assert update profile page is displayed
        Response spnegoResponse = spnegoLogin("hnelson", "secret");
        Assert.assertEquals(200, spnegoResponse.getStatus());
        String responseText = spnegoResponse.readEntity(String.class);
        Assert.assertTrue(responseText.contains("You need to update your user profile to activate your account."));
        Assert.assertTrue(responseText.contains("hnelson@keycloak.org"));

        // Assert user was imported and has required action on him
        assertUser("hnelson", "hnelson@keycloak.org", null, null, true);

        // Switch updateProfileOnFirstLogin to off
        session = keycloakRule.startSession();
        try {
            RealmModel realm = session.realms().getRealm("test");
            UserFederationProviderModel kerberosProviderModel = realm.getUserFederationProviders().get(0);
            kerberosProviderModel.getConfig().put(KerberosConstants.UPDATE_PROFILE_FIRST_LOGIN, "false");
            realm.updateUserFederationProvider(kerberosProviderModel);
        } finally {
            keycloakRule.stopSession(session, true);
        }
    }
}
