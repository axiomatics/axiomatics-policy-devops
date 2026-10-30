package tasks.authhub

import com.axiomatics.apd.authhub.integration.AttributesPullAll
import com.axiomatics.apd.authhub.services.DictionaryService
import extensions.AdmExtension
import tasks.GitGuarded
import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.RemoteConnection

class PullAllAttributesTask extends DefaultTask implements GitGuarded {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @Internal
    boolean allowUnsafe

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PullAllAttributesTask() {
        relativePath.convention("src/authorizationDomain/attributes.yaml")
    }

    @Internal
    @Override
    File getGuardedOutputPath() {
        projectDir.toPath().resolve(relativePath.get()).toFile()
    }

    @TaskAction
    void run() {
        RemoteConnection remote = RemoteConnection.of(adm)
        File outputFile = projectDir.toPath().resolve(relativePath.get()).toFile()
        GitOutputGuard.check(this, projectDir, allowUnsafe, outputFile)
        outputFile.parentFile.mkdirs()
        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

        logger.lifecycle("Pulling attributes from ${remote.label} at ${remote.host} to ${projectDir}")
        remote.withClient { client ->
            def projectId = remote.projectId(client)
            def service = new DictionaryService(client, projectId)
            def summary = new AttributesPullAll(service).execute(outputFile.toPath())
            logger.lifecycle(summary.getMessage())
        }
    }
}
