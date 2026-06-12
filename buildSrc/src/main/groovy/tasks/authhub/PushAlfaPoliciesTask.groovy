package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AlfaPoliciesPushAll
import com.axiomatics.apd.authhub.services.DomainManagerService
import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

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
        if (adm.oidcCredentials == null) {
            throw new RuntimeException("Set oidcCredentials {} on adm " + adm.environment)
        }
        logger.lifecycle("Pushing ALFA policies from ${projectDir} to AuthHub at ${adm.host}")
        def client = new AuthHubClient(adm.host, adm.oidcCredentials.client_id, adm.oidcCredentials.client_secret)
        def projectId = new ProjectIdByName(adm.alfa.namespace, client)
        def service = new DomainManagerService(client, projectId)
        def policyPackageId = new AlfaPoliciesPushAll(service).execute(projectDir.toPath().resolve(relativePath.get()))
        logger.lifecycle("Pushed ALFA policies. Policy package ID: ${policyPackageId}")
    }
}
