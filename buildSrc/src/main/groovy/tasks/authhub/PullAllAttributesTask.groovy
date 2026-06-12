package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AttributesPullAll
import com.axiomatics.apd.authhub.services.DictionaryService
import extensions.AdmExtension
import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class PullAllAttributesTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PullAllAttributesTask() {
        relativePath.convention("src/authorizationDomain/attributes.yaml")
    }

    @TaskAction
    void run() {
        if (adm.oidcCredentials == null) {
            throw new RuntimeException("Set oidcCredentials {} on adm " + adm.environment)
        }
        File outputFile = projectDir.toPath().resolve(relativePath.get()).toFile()
        GitOutputGuard.check(this, outputFile)
        outputFile.parentFile.mkdirs()
        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

        logger.lifecycle("Pulling attributes from AuthHub at ${adm.host} to ${projectDir}")
        def client = new AuthHubClient(adm.host, adm.oidcCredentials.client_id, adm.oidcCredentials.client_secret)
        def projectId = new ProjectIdByName(adm.alfa.namespace, client)
        def service = new DictionaryService(client, projectId)
        new AttributesPullAll(service).execute(outputFile.toPath())
        logger.lifecycle("Pulled attributes to ${outputFile}")
    }
}
