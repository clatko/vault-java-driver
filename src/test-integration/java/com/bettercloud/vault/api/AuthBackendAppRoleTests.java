package com.bettercloud.vault.api;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import com.bettercloud.vault.util.VaultContainer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

/** Integration tests for the AppRole auth backend. */
public class AuthBackendAppRoleTests {

    static String appRoleId;
    static String secretId;

    @ClassRule
    public static final VaultContainer container = new VaultContainer();

    @BeforeClass
    public static void setupClass() throws IOException, InterruptedException, VaultException {
        container.initAndUnsealVault();
        container.setupBackendAppRole();

        final Vault vault = container.getRootVault();

        final LogicalResponse roleIdResponse = vault.logical().read("auth/approle/role/testrole/role-id", false, false);
        appRoleId = roleIdResponse.getData().get("role_id");
        final LogicalResponse secretIdResponse = vault.logical().write("auth/approle/role/testrole/secret-id", null);
        secretId = secretIdResponse.getData().get("secret_id");

        assertNotNull(appRoleId);
        assertNotNull(secretId);
    }

    /** Tests authentication with the app role auth backend */
    @Test
    public void testLoginByAppRole() throws VaultException {
        final Vault vault = container.getVault();
        final String path = "approle";
        final String token = vault.auth().loginByAppRole(path, appRoleId, secretId).getAuthClientToken();

        assertNotNull(token);
        assertNotSame("", token.trim());
    }

}
