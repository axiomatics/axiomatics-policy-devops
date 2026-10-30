package remote

import extensions.AdmExtension
import extensions.AlfaExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import tasks.AdmPullTask
import tasks.AdmPushTask
import tasks.DeprecatedTaskAlias
import tasks.GitGuarded
import tasks.RunAdsTask
import tasks.RunSpawnedAds
import tasks.StopAdmAdsTask
import tasks.WaitAdsStartedTask
import tasks.authhub.PullAllAttributesTask
import tasks.authhub.PullAllConnectorsTask
import tasks.authhub.PullAlfaPoliciesTask
import tasks.authhub.PullPreCheckTask
import tasks.authhub.PushAlfaPoliciesTask
import tasks.authhub.PushAllAttributesTask
import tasks.authhub.PushAllConnectorsTask

/**
 * Registers the tasks of one remote of {@code alfa.repositories}, in group {@code axiomatics-<repoType>-<environment>}
 * (helper tasks in {@code other}):
 * <ul>
 *     <li>every remote: DomainPushTo, DomainPullFrom, DomainPromoteFrom&lt;earlier remote&gt;To (the domain in ADM),
 *     runAdsWith and test (ADS with the remote's domain)</li>
 *     <li>AuthHub: push/pull of dictionary, attribute connectors and policies, and All... of them</li>
 *     <li>ASM: the old names pushTo, pullFrom and promoteFrom...To, deprecated, in group other</li>
 * </ul>
 */
class RemoteTasks {

    static final String OTHER_GROUP = "other"

    private final Project project
    private final AlfaExtension alfa
    private final AdmExtension adm
    private final String env
    // fields, not properties of the remote: the task configuration closures resolve names on the task first
    private final String domainName
    private final String label
    private final String taskGroup
    private final boolean hub
    private final boolean allowUnsafe

    private RemoteTasks(Project project, AlfaExtension alfa, AdmExtension adm) {
        this.project = project
        this.alfa = alfa
        this.adm = adm
        this.env = adm.environment
        this.domainName = adm.domainName
        this.label = labelOf(adm)
        this.taskGroup = groupOf(adm)
        this.hub = adm.repoType == "hub"
        this.allowUnsafe = project.findProperty('apd.allowUnsafe') == 'true'
    }

    /** Registers the tasks of {@code adm}; promote tasks from each of {@code earlierRemotes}. */
    static void register(Project project, AlfaExtension alfa, AdmExtension adm, List<AdmExtension> earlierRemotes) {
        project.logger.info("Registering tasks for remote ${adm.environment}")
        RemoteTasks tasks = new RemoteTasks(project, alfa, adm)
        if (tasks.hub) {
            tasks.registerAuthHubTasks()
        }
        tasks.registerDomainTasks()
        earlierRemotes.each { tasks.registerPromoteFrom(it) }
        tasks.registerAdsTasks()
    }

    static String groupOf(AdmExtension adm) {
        "axiomatics-${adm.repoType}-${adm.environment.toLowerCase()}"
    }

    static String domainPushTo(AdmExtension adm) { "DomainPushTo" + adm.environment }

    static String domainPullFrom(AdmExtension adm) { "DomainPullFrom" + adm.environment }

    static String domainPromote(AdmExtension source, AdmExtension target) {
        "DomainPromoteFrom${source.environment}To${target.environment}"
    }

    /** "AuthHub dev" or "ASM dev", for descriptions. */
    static String labelOf(AdmExtension adm) {
        (adm.repoType == "hub" ? "AuthHub " : "ASM ") + adm.environment
    }


    private void registerDomainTasks() {
        String push = domainPushTo(adm)
        String pull = domainPullFrom(adm)
        project.tasks.register(push, AdmPushTask) {
            group taskGroup
            description "Pushes the domain built by buildAuthzDomain to ${label} as domain '${domainName}'."
            it.adm = this.adm
            domainFile.set(project.tasks.named("buildAuthzDomain").flatMap { it.domainFile })
            dependsOn "buildAuthzDomain"
        }
        project.tasks.register(pull, AdmPullTask) {
            group taskGroup
            description "Pulls the domain '${domainName}' from ${label} to build/alfa/${taskGroup}/domain.yaml."
            it.adm = this.adm
            domainFile.set(project.file("build/alfa/${taskGroup}/domain.yaml"))
        }
        if (!hub) {
            deprecatedAlias("pushTo" + env, push)
            deprecatedAlias("pullFrom" + env, pull)
        }
    }

    private void registerPromoteFrom(AdmExtension source) {
        String promote = domainPromote(source, adm)
        String sourcePull = domainPullFrom(source)
        project.tasks.register(promote, AdmPushTask) {
            group taskGroup
            description "Pulls the domain '${source.domainName}' from ${labelOf(source)} " +
                    "and pushes it to ${label} as domain '${domainName}'."
            it.adm = this.adm
            domainFile.set(project.tasks.named(sourcePull, AdmPullTask).flatMap { it.domainFile })
            dependsOn sourcePull
        }
        if (!hub && source.repoType != "hub") {
            deprecatedAlias("promoteFrom${source.environment}To${env}", promote)
        }
    }

    private void deprecatedAlias(String name, String replacement) {
        project.tasks.register(name, DeprecatedTaskAlias) {
            group OTHER_GROUP
            description "Deprecated, use ${replacement}."
            it.replacement = replacement
            dependsOn replacement
        }
    }

    private void registerAdsTasks() {
        String spawn = "spawnAdsWith" + env + domainName
        String waitStarted = "waitToStartAds" + env
        String stop = "stopAdsFor" + env
        AlfaExtension alfa = this.alfa
        AdmExtension remote = this.adm
        def spawnTask = project.tasks.register(spawn, RunSpawnedAds) {
            group OTHER_GROUP
            description "Starts ADS in the background with the domain '${domainName}' of ${label}."
            it.adm = this.adm
            classpath.from(project.configurations.named("adsRuntimeV2"))
            pipClasspath.from(project.configurations.named("pip"), project.tasks.named("jar"))
            licenseFile.set(project.layout.projectDirectory.file(project.provider { alfa.licenseFile }))
            finalizedBy waitStarted
        }
        project.tasks.register(waitStarted, WaitAdsStartedTask) {
            group OTHER_GROUP
            description "Waits until the ADS started by ${spawn} is ready."
            it.adm = this.adm
            adsLogFile.set(spawnTask.flatMap { it.adsLogFile })
            dependsOn spawn
        }
        project.tasks.register(stop, StopAdmAdsTask) {
            group OTHER_GROUP
            description "Stops the ADS started by ${spawn}."
            it.adm = this.adm
        }
        project.tasks.register("runAdsWith" + env + "-" + domainName, RunAdsTask) {
            group taskGroup
            description "Starts ADS with the domain '${domainName}' of ${label} on a random port and keeps it running."
            it.adm = this.adm
            adsLogFile.set(spawnTask.flatMap { it.adsLogFile })
            dependsOn waitStarted
        }
        project.tasks.register("test" + env + "-" + domainName, Test) {
            group taskGroup
            description "Starts ADS with the domain '${domainName}' of ${label} and runs the tests against it."
            environment "ALFA_TEST_REMOTE_URL", "http://127.0.0.1:${-> remote.adsHttpPort}/authorize"
            environment "ALFA_TEST_REMOTE_USER", AdsApplicationYaml.ADS_USER
            environment "ALFA_TEST_REMOTE_PASSWORD", AdsApplicationYaml.ADS_PASSWORD
            dependsOn waitStarted
            finalizedBy stop
        }
    }

    private void registerAuthHubTasks() {
        String connectorPullFrom = "AttributeConnectorsPullFrom" + env
        String connectorPushTo = "AttributeConnectorsPushTo" + env
        String policyPushTo = "PolicyPushTo" + env
        String policyPullFrom = "PolicyPullFrom" + env
        String dictionaryPushTo = "DictionaryPushTo" + env
        String dictionaryPullFrom = "DictionaryPullFrom" + env
        String preCheck = "preCheckDomainPullFrom" + env
        File projectDir = project.projectDir

        project.tasks.register(connectorPullFrom, PullAllConnectorsTask) {
            group taskGroup
            description "Pulls all attribute connectors from ${label}."
            it.adm = this.adm
            it.projectDir = projectDir
            it.allowUnsafe = this.allowUnsafe
        }
        project.tasks.register(connectorPushTo, PushAllConnectorsTask) {
            group taskGroup
            description "Pushes all attribute connectors to ${label}."
            it.adm = this.adm
            it.projectDir = projectDir
        }
        project.tasks.register(policyPushTo, PushAlfaPoliciesTask) {
            group taskGroup
            description "Pushes all ALFA policies to ${label}."
            it.adm = this.adm
            it.projectDir = projectDir
        }
        project.tasks.register(policyPullFrom, PullAlfaPoliciesTask) {
            group taskGroup
            description "Pulls all ALFA policies from ${label}."
            it.adm = this.adm
            it.projectDir = projectDir
            it.allowUnsafe = this.allowUnsafe
        }
        project.tasks.register(dictionaryPushTo, PushAllAttributesTask) {
            group taskGroup
            description "Pushes the attribute dictionary to ${label}."
            it.adm = this.adm
            it.projectDir = projectDir
        }
        project.tasks.register(dictionaryPullFrom, PullAllAttributesTask) {
            group taskGroup
            description "Pulls the attribute dictionary from ${label}."
            it.adm = this.adm
            it.projectDir = projectDir
            it.allowUnsafe = this.allowUnsafe
        }
        project.tasks.register("AllPushTo" + env) {
            group taskGroup
            description "Pushes dictionary, attribute connectors and ALFA policies to ${label}, in that order."
            dependsOn dictionaryPushTo, connectorPushTo, policyPushTo
        }

        List<String> pulls = [
                dictionaryPullFrom,
                connectorPullFrom,
                policyPullFrom,
                AlfaExtension.GENERATE_ALFA_DICTIONARY_TASK,
                AlfaExtension.GENERATE_JAVA_DICTIONARY_TASK,
        ]
        project.tasks.register(preCheck, PullPreCheckTask) {
            group OTHER_GROUP
            description "Git guard for AllPullFrom${env}: checks that all its output paths are clean before any writes."
            guardedPaths = pulls.collectEntries { String name ->
                [(name): (project.tasks.named(name).get() as GitGuarded).guardedOutputPath]
            }
            rootDir = project.rootDir
            it.allowUnsafe = this.allowUnsafe
        }
        project.tasks.register("AllPullFrom" + env) {
            group taskGroup
            description "Pulls dictionary, attribute connectors and ALFA policies from ${label}, then regenerates the ALFA and Java dictionaries."
            dependsOn([preCheck] + pulls)
        }
        pulls.each { String name ->
            project.tasks.named(name).configure { mustRunAfter preCheck }
        }

        project.tasks.named(connectorPushTo).configure { mustRunAfter dictionaryPushTo }
        project.tasks.named(policyPushTo).configure { mustRunAfter connectorPushTo }
        project.tasks.named(connectorPullFrom).configure { mustRunAfter dictionaryPullFrom }
        project.tasks.named(policyPullFrom).configure { mustRunAfter connectorPullFrom }
        project.tasks.named(AlfaExtension.GENERATE_ALFA_DICTIONARY_TASK).configure { mustRunAfter policyPullFrom }
        project.tasks.named(AlfaExtension.GENERATE_JAVA_DICTIONARY_TASK).configure { mustRunAfter policyPullFrom }
    }
}
