package com.bettercloud.vault.api;

import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.HealthResponse;
import com.bettercloud.vault.rest.Rest;
import com.bettercloud.vault.rest.RestException;
import com.bettercloud.vault.rest.RestResponse;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>The implementing class for operations on Vault's <code>/v1/sys/*</code> REST endpoints, under the
 * "Debug" section of the Vault HTTP API docs (https://www.vaultproject.io/docs/http/index.html).</p>
 *
 * <p>This class is not intended to be constructed directly.  Rather, it is meant to used by way of
 * <code>Vault</code> in a DSL-style builder pattern.  See the Javadoc comments of each <code>public</code>
 * method for usage examples.</p>
 */
public class Debug {

    private final VaultConfig config;

    public Debug(final VaultConfig config) {
        this.config = config;
    }

    /**
     * <p>Returns the health status of Vault. This matches the semantics of a Consul HTTP
     * health check and provides a simple way to monitor the health of a Vault instance.</p>
     *
     * @see <a href="https://www.vaultproject.io/docs/http/sys-health.html">https://www.vaultproject.io/docs/http/sys-health.html</a>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, rootToken);
     * final Vault vault = new Vault(config);
     *
     * final HealthResponse response = vault.sys().debug().health();
     *
     * final Boolean sealed = response.getSealed();  // Warning: CAN be null!
     * }</pre>
     * </blockquote>
     *
     * @return
     * @throws VaultException If any errors occurs with the REST request (e.g. non-200 status code, invalid JSON payload, etc), and the maximum number of retries is exceeded.
     */
    public HealthResponse health() throws VaultException {
        return health(null, null, null, null);
    }

    /**
     * <p>An overloaded version of {@link Debug#health()} that allows for </p>
     * <p>Returns the health status of Vault. This matches the semantics of a Consul HTTP
     * health check and provides a simple way to monitor the health of a Vault instance.</p>
     *
     * @see <a href="https://www.vaultproject.io/docs/http/sys-health.html">https://www.vaultproject.io/docs/http/sys-health.html</a>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, rootToken);
     * final Vault vault = new Vault(config);
     * final HashMap<String, String> params = new HashMap<>();
     * params.put("standbyok", "true");
     *
     * final Map<String, String> response = vault.sys().debug().health(params);
     *
     * final String sealed = response.get("sealed");
     * }</pre>
     * </blockquote>
     *
     * @param params the optional query paramaters
     * @return
     * @throws VaultException If any errors occurs with the REST request (e.g. non-200 status code, invalid JSON payload, etc), and the maximum number of retries is exceeded.
     */

    /**
     * <p>An overloaded version of {@link Debug#health()} that allows for passing one or more optional parameters.</p>
     *
     * <p>WARNING:  In testing, we've found that changing the default HTTP status codes can result in the operation
     * succeeding, but returning an empty JSON payload in the response.  For example, this seems to happen when you
     * set <code>activeCode</code> to 204, but not for 212 (the regular default is 200).  When this happens, the
     * <code>HealthResponse</code> return object will have <code>null</code> values in most of its fields, and you
     * will need to check <code>HealthReponse.getRestResponse().getStatus()</code> to determine the result of
     * the operation.</p>
     *
     * @param standbyOk (optional) Indicates that being a standby should still return the active status code instead of the standby code
     * @param activeCode (optional) Indicates the status code that should be returned for an active node instead of the default of 200
     * @param standbyCode (optional) Indicates the status code that should be returned for a standby node instead of the default of 429
     * @param sealedCode (optional) Indicates the status code that should be returned for a sealed node instead of the default of 500
     * @return
     * @throws VaultException
     */
    public HealthResponse health(
            final Boolean standbyOk,
            final Integer activeCode,
            final Integer standbyCode,
            final Integer sealedCode
    ) throws VaultException {
        final String path = "sys/health";
        int retryCount = 0;
        while (true) {
            try {
                // Build an HTTP request for Vault
                final Rest rest = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/" + path)
                        .connectTimeoutSeconds(config.getOpenTimeout())
                        .readTimeoutSeconds(config.getReadTimeout())
                        .sslPemUTF8(config.getSslPemUTF8())
                        .sslVerification(config.isSslVerify() != null ? config.isSslVerify() : null);
                // Add token if present
                if (config.getToken() != null) {
                    rest.header("X-Vault-Token", config.getToken());
                }
                // Add params if present
                if (standbyOk != null) rest.parameter("standbyok", standbyOk.toString());
                if (activeCode != null) rest.parameter("activecode", activeCode.toString());
                if (standbyCode != null) rest.parameter("standbycode", standbyCode.toString());
                if (sealedCode != null) rest.parameter("sealedcode", sealedCode.toString());
                // Execute request
                final RestResponse restResponse = rest.get();

                // Validate response
                final Set<Integer> validCodes = new HashSet<>();//NOPMD
                validCodes.add(200);
                validCodes.add(429);
                validCodes.add(500);
                if (activeCode != null) validCodes.add(activeCode);
                if (standbyCode != null) validCodes.add(standbyCode);
                if (sealedCode != null) validCodes.add(sealedCode);
                if (!validCodes.contains(restResponse.getStatus())) {
                    throw new VaultException("Vault responded with HTTP status code: " + restResponse.getStatus());
                }
                return new HealthResponse(restResponse, retryCount);
            } catch (RuntimeException | VaultException | RestException e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }

}
