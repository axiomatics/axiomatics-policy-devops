package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AttributeConnectorPushAll
import com.axiomatics.apd.authhub.services.AttributeConnectorService
import com.axiomatics.apd.authhub.services.DictionaryService
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class PushAllConnectorsTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PushAllConnectorsTask() {
        relativePath.convention("src/authorizationDomain/attributeConnectors")
    }

    @TaskAction
    void run() {
        if (adm.oidcCredentials == null) {
            throw new RuntimeException("Set oidcCredentials {} on adm " + adm.environment)
        }
        def inputDir = projectDir.toPath().resolve(relativePath.get())
        logger.lifecycle("Pushing all attribute connectors from ${inputDir} to AuthHub at ${adm.host}")
        def client = new AuthHubClient(adm.host, adm.oidcCredentials.client_id, adm.oidcCredentials.client_secret)
        try {
            def projectId = new ProjectIdByName(adm.alfa.namespace, client)
            def service = new AttributeConnectorService(client, projectId)
            def dictionaryService = new DictionaryService(client, projectId)
            def summary = new AttributeConnectorPushAll(service, dictionaryService).execute(inputDir)
            logger.lifecycle(summary.getMessage())
        } finally {
            client.close()
        }
    }
}
