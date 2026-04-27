package extensions

import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.internal.impldep.org.apache.http.NameValuePair
import org.gradle.internal.impldep.org.apache.http.client.entity.UrlEncodedFormEntity
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpPost
import org.gradle.internal.impldep.org.apache.http.impl.client.CloseableHttpClient
import org.gradle.internal.impldep.org.apache.http.message.BasicNameValuePair

import java.nio.charset.Charset

public class AdmExtension {


    String environment = "default"
    String host;
    String domainName

    String realm = "asm" //asm or hub
    String tokenUri = "/auth/realms/"+realm+"/protocol/openid-connect/token";
    String apiUrlPushStandaloneTemplate = "/api/namespaces/%s/names/%s"
    String apiUrlPushAsmTemplate = "/adm" + apiUrlPushStandaloneTemplate
    String apiUrlPullStandaloneTemplate = apiUrlPushStandaloneTemplate + "/domain"
    String apiUrlPullAsmTemplate = "/adm" + apiUrlPullStandaloneTemplate
    String adsHttpPort = null;

    void realm(String value) {
        this.realm = value
    }
    void tokenUri(String value) {
        this.tokenUri = value
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



    @Nested
    BasicCredentials basicCredentials;
    @Nested
    OidcCredentials oidcCredentials;

    Project project;

    Process process
    AlfaExtension alfa

    @Input
    Map<String, Object> envVariable = new HashMap<>()

    public AdmExtension(Project project, AlfaExtension alfa) {
        this.project = project;
        this.alfa = alfa
        this.domainName = "DoNotUseMe"
    }

    void basicCredentials(Closure c) {
        basicCredentials = new AdmExtension.BasicCredentials();
        project.configure(basicCredentials, c);

    }
    void  envVariable(String k, Object v) {
        envVariable.put(k,v)
    }

    void oidcCredentials(Closure c) {
        oidcCredentials = new AdmExtension.OidcCredentials();
        project.configure(oidcCredentials, c);
    }

    void environment(String spec) {
        environment = spec
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
                ", basicCredentials=" + basicCredentials +
                ", oidcCredentials=" + oidcCredentials +
                ", envVariable=" + envVariable +
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
        String token_uri = "/auth/realms/asm/protocol/openid-connect/token";
        Property<String> access_token;
        String privateToken = null;

        void client_id(String spec) {
            client_id = spec
        }

        void client_secret(String spec) {
            client_secret = spec
        }

        public String login() {
            if (privateToken != null) {
                return privateToken;
            }
            try {
                if (client_id == null || client_secret == null) {
                    throw new RuntimeException("Set client_id and client_secret");
                }
                //Create auth request
                List<NameValuePair> bodyElements = new ArrayList<>();
                bodyElements.add(new BasicNameValuePair("client_id", client_id));
                bodyElements.add(new BasicNameValuePair("client_secret", client_secret));
                bodyElements.add(new BasicNameValuePair("grant_type", "client_credentials"));
                bodyElements.add(new BasicNameValuePair("scope", "openid"));
                UrlEncodedFormEntity authBody = new UrlEncodedFormEntity(bodyElements, (Charset) null);
                String fullURL = AdmExtension.this.host + token_uri;
                project.logger.info("Preforming OIDC login to ADM at ${fullURL}")
                HttpPost post = new HttpPost(fullURL);
                post.setEntity(authBody);
                CloseableHttpClient authClient;

                authClient = HttpClients.createDefault();

                HttpResponse response = authClient.execute(post);

                //check auth response
                int responseCode = response.getStatusLine().getStatusCode();
                String responseText = EntityUtils.toString(response.getEntity());
                project.logger.info("Login call returned ${responseCode}")
                if (responseCode != 200) {
                    throw new RuntimeException(
                            "Failed to perform OpenId Connect authentication to ASM-ADM at " + fullURL + ". Response code: " + responseCode +
                                    ". Response body '" + responseText + "'");

                }
                Map o = (Map) new JsonSlurper().parseText(responseText);
                privateToken = (String) o.get("access_token");
                project.logger.info("Successfully extracted token")
                return privateToken;
            } catch (Exception e) {
                throw new RuntimeException("Failed to login to ADM " + AdmExtension.this.name + " at URL:" + host, e);
            }
        }

        @Override
        public String toString() {
            return "OidcCredentials{" +
                    "client_id='" + client_id + '\'' +
                    ", client_secret='" + "*****" + '\'' +
                    ", token_uri='" + token_uri + '\'' +
                    ", access_token=" + "*******" +
                    '}';
        }
    }


    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof AdmExtension)) return false

        AdmExtension that = (AdmExtension) o

        if (adsHttpPort != that.adsHttpPort) return false
        if (alfa != that.alfa) return false
        if (apiUrlPullAsmTemplate != that.apiUrlPullAsmTemplate) return false
        if (apiUrlPullStandaloneTemplate != that.apiUrlPullStandaloneTemplate) return false
        if (apiUrlPushAsmTemplate != that.apiUrlPushAsmTemplate) return false
        if (apiUrlPushStandaloneTemplate != that.apiUrlPushStandaloneTemplate) return false
        if (basicCredentials != that.basicCredentials) return false
        if (domainName != that.domainName) return false
        if (environment != that.environment) return false
        if (host != that.host) return false
        if (oidcCredentials != that.oidcCredentials) return false
        if (process != that.process) return false
        if (project != that.project) return false
        if (tokenUri != that.tokenUri) return false

        return true
    }

    int hashCode() {
        int result
        result = (environment != null ? environment.hashCode() : 0)
        result = 31 * result + (host != null ? host.hashCode() : 0)
        result = 31 * result + (domainName != null ? domainName.hashCode() : 0)
        result = 31 * result + (tokenUri != null ? tokenUri.hashCode() : 0)
        result = 31 * result + (apiUrlPushStandaloneTemplate != null ? apiUrlPushStandaloneTemplate.hashCode() : 0)
        result = 31 * result + (apiUrlPushAsmTemplate != null ? apiUrlPushAsmTemplate.hashCode() : 0)
        result = 31 * result + (apiUrlPullStandaloneTemplate != null ? apiUrlPullStandaloneTemplate.hashCode() : 0)
        result = 31 * result + (apiUrlPullAsmTemplate != null ? apiUrlPullAsmTemplate.hashCode() : 0)
        result = 31 * result + (adsHttpPort != null ? adsHttpPort.hashCode() : 0)
        result = 31 * result + (basicCredentials != null ? basicCredentials.hashCode() : 0)
        result = 31 * result + (oidcCredentials != null ? oidcCredentials.hashCode() : 0)
        result = 31 * result + (project != null ? project.hashCode() : 0)
        result = 31 * result + (process != null ? process.hashCode() : 0)
        result = 31 * result + (alfa != null ? alfa.hashCode() : 0)
        return result
    }
}