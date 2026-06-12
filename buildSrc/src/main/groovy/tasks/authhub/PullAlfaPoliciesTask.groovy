package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AlfaPoliciesPullAll
import com.axiomatics.apd.authhub.services.DomainManagerService
import extensions.AdmExtension
import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class PullAlfaPoliciesTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PullAlfaPoliciesTask() {
        relativePath.convention("src/authorizationDomain/alfaSpecifications")
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
        def projectId = new ProjectIdByName(adm.alfa.namespace, client)
        def service = new DomainManagerService(client, projectId)
        new AlfaPoliciesPullAll(service).execute(outputDir.toPath())
        logger.lifecycle("Pulled ALFA policies to ${outputDir}")
    }
}
