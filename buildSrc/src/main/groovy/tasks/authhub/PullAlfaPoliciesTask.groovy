package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AlfaPoliciesPullAll
import com.axiomatics.apd.authhub.services.DomainManagerService
import extensions.AdmExtension
import tasks.GitGuarded
import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class PullAlfaPoliciesTask extends DefaultTask implements GitGuarded {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

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
        if (adm.oidcCredentials == null) {
            throw new RuntimeException("Set oidcCredentials {} on adm " + adm.environment)
        }
        File outputDir = projectDir.toPath().resolve(relativePath.get()).toFile()
        GitOutputGuard.check(this, outputDir)
        outputDir.mkdirs()

        logger.lifecycle("Pulling ALFA policies from AuthHub at ${adm.host} to ${projectDir}")
        def client = new AuthHubClient(adm.host, adm.oidcCredentials.client_id, adm.oidcCredentials.client_secret)
        try {
            def projectId = new ProjectIdByName(adm.alfa.namespace, client)
            def service = new DomainManagerService(client, projectId)
            def summary = new AlfaPoliciesPullAll(service).execute(outputDir.toPath())
            logger.lifecycle(summary.getMessage())
        } finally {
            client.close()
        }
    }
}
