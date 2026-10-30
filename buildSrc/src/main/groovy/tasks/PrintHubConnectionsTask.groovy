package tasks

import com.axiomatics.apd.authhub.exception.AuthHubException
import com.axiomatics.apd.authhub.model.ContentType
import extensions.AlfaExtension
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.RemoteConnection
import remote.RemoteTasks

/**
 * Prints, for every remote, the settings an ADS needs to load the remote's domain (client id, token URI, domain URL;
 * never the secret), then downloads the domain and prints its identity and metadata. A remote that cannot be reached
 * is reported and the others are still printed; the task fails at the end. A domain not pushed yet is no failure.
 */
abstract class PrintHubConnectionsTask extends DefaultTask {

    static final int LABEL_WIDTH = "current domain identity: ".length()

    static final String HUB_CONFIGURATION_DOCS =
            "https://docs.axiomatics.com/axiomatics-policy-devops/integrations-cicd/hub-integration#configuration"

    @Internal
    AlfaExtension alfa

    PrintHubConnectionsTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void print() {
        def remotes = alfa.repositories.adms
        if (remotes.isEmpty()) {
            logger.lifecycle("No Authorization Hub connections in build.gradle.")
            logger.lifecycle("To add one, uncomment the hub { } example in alfa { repositories { } } of build.gradle, or see")
            logger.lifecycle(HUB_CONFIGURATION_DOCS)
            return
        }
        List<String> failed = []
        remotes.each { adm ->
            logger.lifecycle("${RemoteTasks.labelOf(adm)} (project ${alfa.projectOf(adm)}, domain ${adm.domainName})")
            try {
                printRemote(RemoteConnection.of(adm))
            } catch (Exception e) {
                logger.error(field("error", e.message))
                failed << RemoteTasks.labelOf(adm)
            }
        }
        if (failed) {
            throw new GradleException("APD: cannot read the domain of ${failed.join(', ')}. The reasons are logged above.")
        }
    }

    // not private: the closure in print() runs on the Gradle-generated subclass of this task
    protected void printRemote(RemoteConnection remote) {
        if (remote.basic) {
            logger.lifecycle(field("username", remote.username))
        } else {
            logger.lifecycle(field("client-id", remote.clientId))
            logger.lifecycle(field("token-uri", remote.tokenUrl))
        }
        remote.withClient { client ->
            def domainManager = remote.domainManager(client)
            logger.lifecycle(field("path", domainManager.getDomainDocumentUrl(remote.domainName)))
            String document
            try {
                document = domainManager.getDomainDocument(remote.domainName, ContentType.JSON)
            } catch (AuthHubException e) {
                if (e.httpStatus == 404) {
                    logger.lifecycle(field("domain", "no domain content available"))
                    return
                }
                throw e
            }
            def domain = new JsonSlurper().parseText(document)
            logger.lifecycle(field("current domain identity", domain.identity))
            (domain.metadata ?: [:]).each { key, value ->
                logger.lifecycle(field("domain-metadata", "${key}: ${value}"))
            }
        }
    }

    // "  label: value", values aligned below each other
    protected static String field(String label, Object value) {
        "  ${(label + ':').padRight(LABEL_WIDTH)}${value}"
    }
}
