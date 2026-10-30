package tasks

import com.axiomatics.apd.authhub.integration.DomainPush
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.RemoteConnection

/**
 * Stores a domain file (default: the one buildAuthzDomain builds) in the ADM of a remote, AuthHub or ASM7, under the
 * remote's domainName. The remote can change independently of anything Gradle observes, so never up-to-date.
 */
abstract class AdmPushTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @InputFile
    abstract RegularFileProperty getDomainFile()

    AdmPushTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void push() {
        RemoteConnection remote = RemoteConnection.of(adm)
        File file = domainFile.get().asFile
        logger.lifecycle("Pushing domain '${remote.domainName}' to ${remote.label} at ${remote.host}")
        remote.withClient { client ->
            def domainManager = remote.domainManager(client)
            logger.lifecycle(new DomainPush(domainManager, remote.domainName).execute(file.toPath()).message)
            // the domain.path of an ADS that loads this domain
            logger.lifecycle("ADS loads this domain from ${domainManager.getDomainDocumentUrl(remote.domainName)}")
        }
    }
}
