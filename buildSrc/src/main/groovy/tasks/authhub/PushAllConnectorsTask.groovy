package tasks.authhub

import com.axiomatics.apd.authhub.integration.AttributeConnectorPushAll
import com.axiomatics.apd.authhub.services.AttributeConnectorService
import com.axiomatics.apd.authhub.services.DictionaryService
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.PromoteMessage
import remote.RemoteConnection

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
        RemoteConnection remote = RemoteConnection.of(adm)
        def inputDir = projectDir.toPath().resolve(relativePath.get())
        logger.lifecycle("Pushing all attribute connectors from ${inputDir} to ${remote.label} at ${remote.host}")
        remote.withClient { client ->
            def projectId = remote.projectId(client)
            def service = new AttributeConnectorService(client, projectId)
            def dictionaryService = new DictionaryService(client, projectId)
            def summary = new AttributeConnectorPushAll(service, dictionaryService, PromoteMessage.of(name, projectDir)).execute(inputDir)
            logger.lifecycle(summary.getMessage())
        }
    }
}
