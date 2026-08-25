package tasks

import extensions.AdmExtension
import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import java.nio.charset.Charset
import java.util.function.Supplier

// Pushes/pulls the authorization domain to/from a remote ADM (AuthHub/ASM) system. The remote's
// state can change independently of anything Gradle can observe locally, so these tasks are never
// up-to-date by design — always rerun.
abstract class AbstractAdmTask extends DefaultTask {

    @Internal
    AdmExtension adm

    AbstractAdmTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void run() {

        if (adm.basicCredentials == null && adm.oidcCredentials == null ||
                (adm.basicCredentials != null && adm.oidcCredentials != null)) {
            throw new RuntimeException("Set either basicCredentials {} or oidcCredentails {} on adm " + adm.name);
        }

        boolean isBasic = adm.basicCredentials != null
        logger.info("Auth to ADM, basicAuth (else token auth): ${isBasic}. CWD " + new File(".").getAbsolutePath() )
        def fullUrl = getUrl()
        def body = getMakeBody()
        URLConnection req = new URL(fullUrl).openConnection()
        def truststoreFile  = ""+ System.getProperty("javax.net.ssl.trustStorePassword")
        boolean readable = new File(truststoreFile).canRead();
        def truststoreFileAbsolute =  new File(truststoreFile).getAbsolutePath()
        logger.info("Truststore file from system properties: '" + truststoreFileAbsolute + "', file exists and readable: " + readable);
        logger.lifecycle("Calling "+getServiceName()+" at " + fullUrl.toString());
        if (isBasic) {
            req.setRequestProperty("Authorization", getBasicAuthenticationHeader(
                    adm.basicCredentials.username, adm.basicCredentials.password))
        } else {
            req.setRequestProperty("Authorization", "Bearer ${doOIDCLoginAndGetAccessToken()}");
        }

        req.setRequestMethod(getRequestMethod())
        req.setDoOutput(true)
        if (body != null) {
            req.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            req.getOutputStream().write(body.getBytes("UTF-8"))
        }
        if (req.getResponseCode() != getExpectedHttpReturnCode()) {
            throw new RuntimeException("${getRequestMethod()} domain to ${fullUrl} failed with ${req.responseCode}, " +
                    "expected ${getExpectedHttpReturnCode()}. Check your URL to ASM. Respone body: " + req.inputStream.text)
        }
        handleResposne(() -> req.inputStream.getText("UTF-8"))

    }


    private doOIDCLoginAndGetAccessToken() {
        //Create auth request
        AdmExtension.OidcCredentials oidc = adm.oidcCredentials
        List<NameValuePair> bodyElements = new ArrayList<>();
        bodyElements.add(new BasicNameValuePair("client_id", oidc.client_id));
        bodyElements.add(new BasicNameValuePair("client_secret", oidc.client_secret));
        bodyElements.add(new BasicNameValuePair("grant_type", "client_credentials"));
        bodyElements.add(new BasicNameValuePair("scope", "openid"));
        UrlEncodedFormEntity authBody = new UrlEncodedFormEntity(bodyElements, (Charset) null);
        String url = adm.host + oidc.tokenUri;
        HttpPost post = new HttpPost(url);
        post.setEntity(authBody);
        HttpClient authClient;

        authClient = HttpClients.createDefault()
        HttpResponse response;
        try {
            response = authClient.execute(post);
        } catch (Exception e) {
            throw new RuntimeException("OIDC login failed to '${url}': " + e.getMessage(), e)
        }

        //check auth response
        int responseCode = response.getStatusLine().getStatusCode()
        if (responseCode != 200) {
            throw new RuntimeException("Failed to perform OpenId Connect authentication to ASM-ADM at ${url}. Response code: ${responseCode}." +
                    " Response body '${EntityUtils.toString(response.getEntity())}'")
        }
        new JsonSlurper().parseText(EntityUtils.toString(response.getEntity())).access_token
    }


    private static final String getBasicAuthenticationHeader(String username, String password) {
        String valueToEncode = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }

    @Internal
    protected String getUrl() {
        boolean isBasic = adm.basicCredentials != null
        String template;
        if (isBasic) {
            template =  adm.host + getAdmUrlTemplate()
        } else {
          template = adm.host + getAsmAdmUrlTemplate()
        }
        String.format(template, adm.alfa.namespace, adm.domainName)
    }

    @Internal
    protected abstract String getMakeBody();

    @Internal
    protected abstract String getRequestMethod();

    @Internal
    protected abstract String getAdmUrlTemplate();

    @Internal
    protected abstract String getAsmAdmUrlTemplate();

    @Internal
    protected abstract int getExpectedHttpReturnCode();

    @Internal
    protected abstract String getServiceName();


    protected abstract void handleResposne(Supplier<String> response);


}

