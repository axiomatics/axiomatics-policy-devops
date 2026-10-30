package remote

/**
 * The application.yml of an ADS that loads its domain from the ADM of a remote (spawnAdsWith...). ADS authenticates
 * at ADM like the remote is configured (basic, or OIDC client credentials), and tests reach ADS with
 * {@link #ADS_USER}/{@link #ADS_PASSWORD}.
 *
 * <p>Configured values are written as single-quoted YAML strings, so characters like {@code #} and {@code :} are kept.
 * Spring still resolves {@code ${...}} placeholders inside them.
 */
class AdsApplicationYaml {

    static final String ADS_USER = "pdp-user"
    static final String ADS_PASSWORD = "secret"
    private static final String ADM_CLIENT = "adm-client"
    private static final String TLS_BUNDLE = "admBundle"

    String licenseFile
    String domainUrl
    // basic credentials, or OIDC client credentials with the token endpoint URL
    String username
    String password
    String clientId
    String clientSecret
    String tokenUrl
    // trust store for TLS towards ADM, optional
    String trustStore
    String trustStorePassword
    String trustStoreType = "JKS"
    boolean verbose

    /** Settings from a remote and this JVM's javax.net.ssl.trustStore* system properties. */
    static AdsApplicationYaml of(RemoteConnection remote, String domainUrl, String licenseFile, boolean verbose) {
        new AdsApplicationYaml(
                licenseFile: licenseFile,
                domainUrl: domainUrl,
                username: remote.username,
                password: remote.password,
                clientId: remote.clientId,
                clientSecret: remote.clientSecret,
                tokenUrl: remote.tokenUrl,
                trustStore: System.getProperty("javax.net.ssl.trustStore"),
                trustStorePassword: System.getProperty("javax.net.ssl.trustStorePassword"),
                trustStoreType: System.getProperty("javax.net.ssl.trustStoreType", "JKS"),
                verbose: verbose)
    }

    String render() {
        boolean tls = trustStore != null
        StringBuilder yaml = new StringBuilder()
        yaml << "license: ${q(fileUri(licenseFile))}\n"
        yaml << "\n"
        yaml << "domain:\n"
        yaml << "  path: ${q(domainUrl)}\n"
        yaml << "  authentication:\n"
        if (username != null) {
            yaml << "    username: ${q(username)}\n"
            yaml << "    password: ${q(password)}\n"
        } else {
            yaml << "    oauth2ClientId: ${ADM_CLIENT}\n"
        }
        if (tls) {
            yaml << "    tlsConfigurationId: ${TLS_BUNDLE}\n"
        }
        yaml << "\n"
        yaml << "spring:\n"
        yaml << "  security:\n"
        yaml << "    user:\n"
        yaml << "      name: ${ADS_USER}\n"
        yaml << "      password: ${ADS_PASSWORD}\n"
        if (username == null) {
            yaml << "    oauth2:\n"
            yaml << "      client:\n"
            yaml << "        registration:\n"
            yaml << "          ${ADM_CLIENT}:\n"
            yaml << "            client-id: ${q(clientId)}\n"
            yaml << "            client-secret: ${q(clientSecret)}\n"
            yaml << "            scope: openid\n"
            yaml << "            authorization-grant-type: client_credentials\n"
            yaml << "            provider: ${ADM_CLIENT}\n"
            yaml << "        provider:\n"
            yaml << "          ${ADM_CLIENT}:\n"
            yaml << "            token-uri: ${q(tokenUrl)}\n"
        }
        if (tls) {
            yaml << "  ssl:\n"
            yaml << "    bundle:\n"
            yaml << "      jks:\n"
            yaml << "        ${TLS_BUNDLE}:\n"
            yaml << "          truststore:\n"
            yaml << "            location: ${q(fileUri(trustStore))}\n"
            if (trustStorePassword != null) {
                yaml << "            password: ${q(trustStorePassword)}\n"
            }
            yaml << "            type: ${q(trustStoreType)}\n"
        }
        yaml << "\n"
        yaml << "logging:\n"
        yaml << "  level:\n"
        yaml << "    root: WARN\n"
        yaml << "    org.apache.jcs: \${LOGLEVEL:${verbose ? "WARN" : "INFO"}}\n"
        yaml << "    com.axiomatics: \${LOGLEVEL:${verbose ? "DEBUG" : "INFO"}}\n"
        yaml.toString()
    }

    /**
     * The file: URI of a path, as ADS reads the license and trust store locations. Not "file:" + path: on Windows that
     * is file:C:\..., which ADS 26.2 rejects (no backslashes in a URI); toURI() gives file:/C:/....
     */
    static String fileUri(String path) {
        new File(path).toURI().toString()
    }

    /** A single-quoted YAML string. */
    static String q(String value) {
        "'" + (value == null ? "" : value.replace("'", "''")) + "'"
    }
}
