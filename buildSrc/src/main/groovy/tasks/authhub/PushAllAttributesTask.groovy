package tasks.authhub

import com.axiomatics.apd.authhub.integration.AttributesPushAll
import com.axiomatics.apd.authhub.services.DictionaryService
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.PromoteMessage
import remote.RemoteConnection

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
        RemoteConnection remote = RemoteConnection.of(adm)
        logger.lifecycle("Pushing attributes from ${projectDir} to ${remote.label} at ${remote.host}")
        remote.withClient { client ->
            def projectId = remote.projectId(client)
            def service = new DictionaryService(client, projectId)
            def summary = new AttributesPushAll(service, PromoteMessage.of(name, projectDir)).execute(projectDir.toPath().resolve(relativePath.get()), continueOnHttp4xx.get())
            logger.lifecycle(summary.getMessage())
            // every attribute is tried first (continueOnHttp4xx), so one run reports all rejected attributes
            if (summary.failed) {
                throw new GradleException("APD: ${remote.label} rejected ${summary.failed.size()} attribute(s): " +
                        "${summary.failed}. The reasons are logged above.")
            }
        }
    }
}
