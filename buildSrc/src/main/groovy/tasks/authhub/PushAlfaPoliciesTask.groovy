package tasks.authhub

import com.axiomatics.apd.authhub.integration.AlfaPoliciesPushAll
import com.axiomatics.apd.authhub.services.PolicyPackageService
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import remote.PromoteMessage
import remote.RemoteConnection

class PushAlfaPoliciesTask extends DefaultTask {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PushAlfaPoliciesTask() {
        relativePath.convention("src/authorizationDomain/alfaSpecifications")
    }

    @TaskAction
    void run() {
        RemoteConnection remote = RemoteConnection.of(adm)
        logger.lifecycle("Pushing ALFA policies from ${projectDir} to ${remote.label} at ${remote.host}")
        remote.withClient { client ->
            def projectId = remote.projectId(client)
            def service = new PolicyPackageService(client, projectId)
            def summary = new AlfaPoliciesPushAll(service, PromoteMessage.of(name, projectDir)).execute(projectDir.toPath().resolve(relativePath.get()))
            logger.lifecycle(summary.getMessage())
        }
    }
}
