package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AttributesPushAll
import com.axiomatics.apd.authhub.services.DictionaryService
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class PushAllAttributesTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<Boolean> continueOnHttp4xx = project.objects.property(Boolean)

    PushAllAttributesTask() {
        relativePath.convention("src/authorizationDomain/attributes.yaml")
        continueOnHttp4xx.convention(true)
    }

    @TaskAction
    void run() {
        if (adm.oidcCredentials == null) {
            throw new RuntimeException("Set oidcCredentials {} on adm " + adm.environment)
        }
        logger.lifecycle("Pushing attributes from ${projectDir} to AuthHub at ${adm.host}")
        def client = new AuthHubClient(adm.host, adm.oidcCredentials.client_id, adm.oidcCredentials.client_secret)
        def projectId = new ProjectIdByName(adm.alfa.namespace, client)
        def service = new DictionaryService(client, projectId)
        def results = new AttributesPushAll(service).execute(projectDir.toPath().resolve(relativePath.get()), continueOnHttp4xx.get())
        results.forEach { fqn, result -> logger.lifecycle("  ${fqn}: ${result}") }
    }
}
