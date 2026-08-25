package tasks.authhub

import com.axiomatics.apd.authhub.client.AuthHubClient
import com.axiomatics.apd.authhub.client.ProjectIdByName
import com.axiomatics.apd.authhub.integration.AttributeConnectorPullAll
import com.axiomatics.apd.authhub.services.AttributeConnectorService
import extensions.AdmExtension
import tasks.GitGuarded
import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class PullAllConnectorsTask extends DefaultTask implements GitGuarded {

    @Internal
    AdmExtension adm

    @Internal
    File projectDir

    @Internal
    boolean allowUnsafe

    @org.gradle.api.tasks.Input
    final org.gradle.api.provider.Property<String> relativePath = project.objects.property(String)

    PullAllConnectorsTask() {
        relativePath.convention("src/authorizationDomain/attributeConnectors")
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
        GitOutputGuard.check(this, projectDir, allowUnsafe, outputDir)
        outputDir.mkdirs()

        logger.lifecycle("Pulling all attribute connectors from AuthHub at ${adm.host} to ${projectDir}")
        def client = new AuthHubClient(adm.host, adm.oidcCredentials.client_id, adm.oidcCredentials.client_secret)
        try {
            def projectId = new ProjectIdByName(adm.alfa.namespace, client)
            def service = new AttributeConnectorService(client, projectId)
            def summary = new AttributeConnectorPullAll(service, projectDir.toPath(), relativePath.get()).execute()
            logger.lifecycle(summary.getMessage())
        } finally {
            client.close()
        }
    }

    void adm(Closure closure) {
        closure.delegate = adm
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
    }
}
