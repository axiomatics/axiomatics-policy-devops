package remote

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ExplicitProjectId
import com.axiomatics.apd.authhub.client.ProjectId
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.services.AdmEndpoints
import com.axiomatics.apd.authhub.services.DomainManagerService
import extensions.AdmExtension
import org.gradle.api.GradleException

/**
 * How a remote of {@code alfa.repositories} is reached: the one translation of an {@link AdmExtension} into
 * authhub-client settings. Values are copied when created (at task execution), and checked:
 * <ul>
 *     <li>exactly one of {@code basicCredentials {}} and {@code oidcCredentials {}}; AuthHub needs OIDC</li>
 *     <li>OIDC token endpoint: {@link AdmExtension#effectiveTokenUri()}</li>
 *     <li>project: the remote's own {@code project}, otherwise the deprecated {@code alfa { project }}, otherwise the
 *     root project name ({@link AlfaExtension#projectOf})</li>
 *     <li>AuthHub: ADM of AuthHub, namespace = id of the AuthHub project with that name</li>
 *     <li>ASM7 ({@code adm {}}): ADM at the configured URL templates (the Standalone templates with basic
 *     credentials, the Asm templates with OIDC), namespace = the project; domain push/pull only</li>
 * </ul>
 */
class RemoteConnection {

    final String environment
    final String host
    final boolean hub
    final String namespace
    final String domainName
    final AdmEndpoints admEndpoints
    final String tokenPath

    private final String username
    private final String password
    private final String clientId
    private final String clientSecret

    static RemoteConnection of(AdmExtension adm) {
        new RemoteConnection(adm)
    }

    private RemoteConnection(AdmExtension adm) {
        environment = adm.environment
        host = adm.host
        hub = adm.repoType == "hub"
        namespace = adm.alfa.projectOf(adm)
        domainName = adm.domainName
        label = RemoteTasks.labelOf(adm)
        if (!host) {
            throw new GradleException("Set host on remote '${environment}'")
        }
        boolean basic = adm.basicCredentials != null
        if (basic == (adm.oidcCredentials != null)) {
            throw new GradleException("Set either basicCredentials {} or oidcCredentials {} on remote '${environment}'")
        }
        if (hub && basic) {
            throw new GradleException("Set oidcCredentials {} on AuthHub remote '${environment}': AuthHub does not support basicCredentials {}")
        }
        username = basic ? adm.basicCredentials.username : null
        password = basic ? adm.basicCredentials.password : null
        clientId = basic ? null : adm.oidcCredentials.client_id
        clientSecret = basic ? null : adm.oidcCredentials.client_secret
        tokenPath = basic ? null : adm.effectiveTokenUri()
        admEndpoints = basic ? AdmEndpoints.of(adm.apiUrlPushStandaloneTemplate, adm.apiUrlPullStandaloneTemplate)
                : hub ? AdmEndpoints.HUB
                : AdmEndpoints.of(adm.apiUrlPushAsmTemplate, adm.apiUrlPullAsmTemplate)
        if (basic) {
            requireSet(username, "basicCredentials.username")
            requireSet(password, "basicCredentials.password")
        } else {
            requireSet(clientId, "oidcCredentials.client_id")
            requireSet(clientSecret, "oidcCredentials.client_secret")
        }
    }

    private void requireSet(String value, String name) {
        if (!value) {
            throw new GradleException("Set ${name} on remote '${environment}'")
        }
    }

    boolean isBasic() {
        username != null
    }

    String getUsername() { username }

    String getPassword() { password }

    String getClientId() { clientId }

    String getClientSecret() { clientSecret }

    /** Absolute URL of the token endpoint (OIDC only). */
    String getTokenUrl() {
        tokenPath == null ? null : host.replaceAll('/+$', '') + tokenPath
    }

    /** "AuthHub dev" or "ASM dev", for log messages. */
    final String label

    /** A new client for this remote; the caller closes it. */
    AuthHubClient openClient() {
        basic ? AuthHubClient.withBasicAuth(host, username, password)
                : new AuthHubClient(host, clientId, clientSecret, tokenPath)
    }

    ProjectId projectId(AuthHubClient client) {
        hub ? new ProjectIdByName(namespace, client) : new ExplicitProjectId(namespace)
    }

    DomainManagerService domainManager(AuthHubClient client) {
        new DomainManagerService(client, projectId(client), admEndpoints)
    }

    /** Runs the action with a new client, closed afterwards. */
    def <T> T withClient(Closure<T> action) {
        AuthHubClient client = openClient()
        try {
            action(client)
        } finally {
            client.close()
        }
    }
}
