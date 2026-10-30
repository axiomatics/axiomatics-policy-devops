package tasks.authhub

import com.axiomatics.apd.authhub.integration.AlfaPoliciesPullAll
import com.axiomatics.apd.authhub.services.PolicyPackageService
import extensions.AdmExtension
import tasks.GitGuarded
import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.RemoteConnection

class PullAlfaPoliciesTask extends DefaultTask implements GitGuarded {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @Internal
    boolean allowUnsafe

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PullAlfaPoliciesTask() {
        relativePath.convention("src/authorizationDomain/alfaSpecifications")
    }

    @Internal
    @Override
    File getGuardedOutputPath() {
        projectDir.toPath().resolve(relativePath.get()).toFile()
    }

    @TaskAction
    void run() {
        RemoteConnection remote = RemoteConnection.of(adm)
        File outputDir = projectDir.toPath().resolve(relativePath.get()).toFile()
        GitOutputGuard.check(this, projectDir, allowUnsafe, outputDir)
        outputDir.mkdirs()

        logger.lifecycle("Pulling ALFA policies from ${remote.label} at ${remote.host} to ${projectDir}")
        remote.withClient { client ->
            def projectId = remote.projectId(client)
            def service = new PolicyPackageService(client, projectId)
            def summary = new AlfaPoliciesPullAll(service).execute(outputDir.toPath())
            logger.lifecycle(summary.getMessage())
        }
    }
}
