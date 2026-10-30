package extensions

import org.gradle.api.Project

/**
 * A remote declared in {@code alfa { repositories { ... } }}: AuthHub ({@code hub {}} or {@code remote {}}, the
 * default) or the ADM of ASM7 ({@code adm {}}). Configuration only; how the remote is reached is decided by
 * {@code remote.RemoteConnection}.
 */
public class AdmExtension {

    static final String TOKEN_PATH_TEMPLATE = "/auth/realms/%s/protocol/openid-connect/token"

    // names the tasks of this remote; optional, set to <project>_<domainName> at registration when not set
    String environment
    String host;
    String domainName
    // AuthHub project (ASM7: namespace) of this remote; set with project 'x' (the field project is Gradle's Project)
    String projectName

    String realm = "hub"//asm or hub
    // token endpoint path; when not set it is derived from realm, see effectiveTokenUri()
    String tokenUri
    String apiUrlPushStandaloneTemplate = "/adm/namespaces/%s/names/%s"
    String apiUrlPushAsmTemplate = "/api" + apiUrlPushStandaloneTemplate
    String apiUrlPullStandaloneTemplate = apiUrlPushStandaloneTemplate + "/domain"
    String apiUrlPullAsmTemplate = "/api" + apiUrlPullStandaloneTemplate
    String adsHttpPort = null;
    String repoType = "hub" //asm or hub etc

    void realm(String value) {
        this.realm = value
    }
    void tokenUri(String value) {
        this.tokenUri = value
    }

    /**
     * The OIDC token endpoint path in use, first set of: {@code oidcCredentials.token_uri}, the token endpoint of
     * {@code oidcCredentials.realm}, {@code tokenUri}, the token endpoint of {@code realm} (default {@code hub}).
     */
    String effectiveTokenUri() {
        oidcCredentials?.token_uri ?: tokenPath(oidcCredentials?.realm) ?: tokenUri ?: tokenPath(realm)
    }

    private static String tokenPath(String realm) {
        realm ? String.format(TOKEN_PATH_TEMPLATE, realm) : null
    }

    void apiUrlPushStandaloneTemplate(String value) {
        this.apiUrlPushStandaloneTemplate = value
    }
    void apiUrlPushAsmTemplate(String value) {
        this.apiUrlPushAsmTemplate = value
    }
    void apiUrlPullStandaloneTemplate(String value) {
        this.apiUrlPullStandaloneTemplate = value
    }
    void apiUrlPullAsmTemplate(String value) {
        this.apiUrlPullAsmTemplate = value
    }
    void adsHttpPort(String value) {
        this.adsHttpPort = value
    }
    void repoType(String value) {
        this.repoType = value
    }

    BasicCredentials basicCredentials;
    OidcCredentials oidcCredentials;

    Project project;

    // the ADS started by spawnAdsWith<environment><domainName>
    Process process
    AlfaExtension alfa

    Map<String, Object> envVariable = new HashMap<>()

    public AdmExtension(Project project, AlfaExtension alfa) {
        this.project = project;
        this.alfa = alfa
    }

    void basicCredentials(@DelegatesTo(value = BasicCredentials, strategy = Closure.DELEGATE_FIRST) Closure c) {
        basicCredentials = new AdmExtension.BasicCredentials();
        project.configure(basicCredentials, c);

    }
    void  envVariable(String k, Object v) {
        envVariable.put(k,v)
    }

    void oidcCredentials(@DelegatesTo(value = OidcCredentials, strategy = Closure.DELEGATE_FIRST) Closure c) {
        oidcCredentials = new AdmExtension.OidcCredentials();
        project.configure(oidcCredentials, c);
    }

    void environment(String spec) {
        environment = spec
    }

    /**
     * The AuthHub project of this remote (ASM7: the namespace); takes priority over the deprecated
     * {@code alfa { project }}. Only the method form works: {@code project = 'x'} would assign Gradle's Project.
     */
    void project(String name) {
        projectName = name
    }

    void host(String spec) {
        host = spec
    }

    void domainName(String spec) {
        domainName = spec
    }

    @Override
    public String toString() {
        return "AdmExtension{" +
                "environment='" + environment + '\'' +
                ", host='" + host + '\'' +
                ", domainName='" + domainName + '\'' +
                ", project='" + projectName + '\'' +
                ", realm='" + realm + '\'' +
                ", repoType='" + repoType + '\'' +
                ", basicCredentials=" + basicCredentials +
                ", oidcCredentials=" + oidcCredentials +
                ", envVariable=" + envVariable.keySet() +
                '}';
    }


    public class BasicCredentials {
        String username;
        String password;

        @Override
        public String toString() {
            return "BasicCredentials{" +
                    "username='" + username + '\'' +
                    ", password='" + "*****" + '\'' +
                    '}';
        }

        void username(String spec) {
            this.username = spec
        }

        void password(String spec) {
            this.password = spec
        }
    }

    public class OidcCredentials {
        String client_id;
        String client_secret;
        // when set, these override the token endpoint of the remote, see AdmExtension.effectiveTokenUri()
        String realm
        String token_uri

        void realm(String spec) {
            realm = spec;
        }
        void client_id(String spec) {
            client_id = spec
        }

        void client_secret(String spec) {
            client_secret = spec
        }

        @Override
        public String toString() {
            return "OidcCredentials{" +
                    "client_id='" + client_id + '\'' +
                    ", client_secret='" + "*****" + '\'' +
                    ", realm='" + realm + '\'' +
                    ", token_uri='" + token_uri + '\'' +
                    '}';
        }
    }
}
