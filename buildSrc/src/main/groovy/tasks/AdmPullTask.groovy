package tasks

import com.axiomatics.apd.authhub.integration.DomainPull
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import remote.RemoteConnection

/**
 * Writes the domain named domainName in the ADM of a remote, AuthHub or ASM7, to a YAML file. The remote can change
 * independently of anything Gradle observes, so never up-to-date.
 */
abstract class AdmPullTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @OutputFile
    abstract RegularFileProperty getDomainFile()

    AdmPullTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void pull() {
        RemoteConnection remote = RemoteConnection.of(adm)
        File file = domainFile.get().asFile
        logger.lifecycle("Pulling domain '${remote.domainName}' from ${remote.label} at ${remote.host}")
        remote.withClient { client ->
            logger.lifecycle(new DomainPull(remote.domainManager(client), remote.domainName).execute(file.toPath()).message)
        }
    }
}
