package extensions

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import tasks.*
import tasks.authhub.*
import tasks.dictionary.*

abstract class AlfaExtension {

    String namespace
    String mainpolicy = null
    String deploymentDescriptor = "deployment.yaml"
    String srcDir = "src/authorizationDomain"
    String licenseFile = "license/axiomatics_ADS.license"
    String dockerName
    boolean extraAttributeIdCheck = true
    Project project
    Map<String,String> metadata = new HashMap<>()
    Boolean withVisualTrace = true

    // Identity string for the generated authorization domain. Defaults to a git-derived
    // "repo-commit" value (see GitInfoValueSource) but stays overridable — an explicit
    // `alfa { domainIdentity = 'x' }` always wins over the convention.
    abstract Property<String> getDomainIdentity()

    DomainRepositories repositories = new DomainRepositories();

    private Provider<Map<String, String>> gitInfo

    AlfaExtension(Project project) {
        this.project = project;
        namespace = this.project.getRootProject().getName().replaceAll("-", "_").replaceAll("\\.", "_")
        gitInfo = project.providers.of(GitInfoValueSource) {
            it.parameters.rootDir.set(project.layout.projectDirectory)
        }
        domainIdentity.convention(gitInfo.map { it.repoAndCommit })
    }

    void repositories(Closure c) {
        project.configure(repositories, c);
        repositories.adms.forEach(adm -> addAdmTasksFor(adm, this))
    }

    void namespace(String spec) {
        this.namespace = spec
    }
    void project(String spec) {
        namespace(spec)
    }

    @Override
    public String toString() {
        return "AlfaExtension{" +
                "namespace='" + namespace + '\'' +
                ", mainpolicy='" + mainpolicy + '\'' +
                ", deploymentDescriptor='" + deploymentDescriptor + '\'' +
                ", srcDir='" + srcDir + '\'' +
                ", licenseFile='" + licenseFile + '\'' +
                ", dockerName='" + dockerName + '\'' +
                ", project=" + project +
                ", repositories=" + repositories +
                ", withVisualTrace=" + withVisualTrace +
                ", metadata =" + metadata +
                '}';
    }


    static final String GENERATE_ALFA_DICTIONARY_TASK = "generateAlfaDictionaryFromYaml"
    static final String GENERATE_JAVA_DICTIONARY_TASK = "generateJavaDictionaryFromYaml"

    Provider<RegularFile> getAttributesYamlFile() {
        project.layout.projectDirectory.file(project.provider { "${srcDir}/attributes.yaml" })
    }

    Provider<RegularFile> getAttributesAlfaFile() {
        project.layout.projectDirectory.file(project.provider { "${srcDir}/alfaSpecifications/attributes.alfa" })
    }

    Provider<RegularFile> getDictionaryJavaFile() {
        project.layout.projectDirectory.file(project.provider {
            File testJavaDir = project.sourceSets.test.java.srcDirs.first()
            new File(testJavaDir, 'apd/Dictionary.java').path
        })
    }

    Provider<String> getGitCommitAuthor() {
        gitInfo.map { it.author }
    }

    Provider<String> getGitCommitMessage() {
        gitInfo.map { it.message }
    }

    Provider<String> getCommitRef() {
        gitInfo.map { it.commitRef }
    }

    Provider<String> getCommitDate() {
        gitInfo.map { it.commitDate }
    }

    def addAdmTasksFor = { AdmExtension lAdm, AlfaExtension lAlfa ->
        project.logger.info("Registring tasks for adm with environment ${lAdm.environment}")
        boolean allowUnsafeRequested = project.findProperty('apd.allowUnsafe') == 'true'
        String admId = lAdm.environment
        String specificGroup = "axiomatics-${lAdm.repoType}-${lAdm.environment.toLowerCase()}"
        boolean isHub = lAdm.repoType == "hub"
        String GROUP_FOR_AUTHHUB_TASKS = specificGroup;
        String pullFrom ="pullFrom"
        String connectorPullFrom = "AttributeConnectorsPullFrom" + admId
        String connectorPushTo = "AttributeConnectorsPushTo" + admId
        String policyPushTo = "PolicyPushTo" + admId
        String policyPullFrom = "PolicyPullFrom" + admId
        String dictionaryPushTo = "DictionaryPushTo" + admId
        String dictionaryPullFrom = "DictionaryPullFrom" + admId
        String promoteFrom = "promoteFrom"
        String domainPushTo = "AllPushTo" + admId
        String domainPullFrom = "AllPullFrom" + admId
        String pullFromAdm = pullFrom + admId
        String pushToAdm = "pushTo" + admId
        String spawnAdsWithAdm = "spawnAdsWith" + admId+lAdm.domainName
        String runAdsWithAdm = "runAdsWith" + admId+"-"+lAdm.domainName
        String waitToStartAdsForAdm = "waitToStartAds" + admId
        String testAdm = "test" + admId+"-"+lAdm.domainName
        String stopAdsForAdm = "stopAdsFor" + admId

        if (isHub) {
            project.tasks.register(connectorPullFrom, PullAllConnectorsTask.class) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pulls all attribute connectors from AuthHub ${admId}."
                adm = lAdm
                projectDir = project.file(".")
                allowUnsafe = allowUnsafeRequested
            }

            project.tasks.register(connectorPushTo, PushAllConnectorsTask.class) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pushes all attribute connectors to AuthHub ${admId}."
                adm = lAdm
                projectDir = project.file(".")
            }

            project.tasks.register(policyPushTo, PushAlfaPoliciesTask.class) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pushes all ALFA policies to AuthHub ${admId}."
                adm = lAdm
                projectDir = project.file(".")
            }

            project.tasks.register(policyPullFrom, PullAlfaPoliciesTask.class) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pulls all ALFA policies from AuthHub ${admId}."
                adm = lAdm
                projectDir = project.file(".")
                allowUnsafe = allowUnsafeRequested
            }

            project.tasks.register(dictionaryPushTo, PushAllAttributesTask.class) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pushes the attribute dictionary to AuthHub ${admId}."
                adm = lAdm
                projectDir = project.file(".")
            }

            project.tasks.register(dictionaryPullFrom, PullAllAttributesTask.class) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pulls the attribute dictionary from AuthHub ${admId}."
                adm = lAdm
                projectDir = project.file(".")
                allowUnsafe = allowUnsafeRequested
            }

            project.tasks.register(domainPushTo) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pushes the entire authorization domain to AuthHub ${admId}: attributes, then connectors, then policies."
                dependsOn dictionaryPushTo, connectorPushTo, policyPushTo
            }

            List<String> domainPullFromDeps = [
                    dictionaryPullFrom,
                    connectorPullFrom,
                    policyPullFrom,
                    GENERATE_ALFA_DICTIONARY_TASK,
                    GENERATE_JAVA_DICTIONARY_TASK,
            ]
            String preCheckDomainPullFrom = "preCheckDomainPullFrom" + admId

            project.tasks.register(preCheckDomainPullFrom) { Task preCheckTask ->
                group 'other'
                description "Pre-flight git guard for DomainPullFrom${admId}: checks all output paths are clean before any writes."
                Map<String, File> guardedPaths = domainPullFromDeps.collectEntries { String taskName ->
                    [(taskName): (project.tasks.named(taskName).get() as GitGuarded).guardedOutputPath]
                }
                File rootDir = project.rootDir
                preCheckTask.doFirst {
                    GitOutputGuard.checkAll(preCheckTask, rootDir, allowUnsafeRequested, guardedPaths)
                }
            }

            project.tasks.register(domainPullFrom) {
                group GROUP_FOR_AUTHHUB_TASKS
                description "Pulls the entire authorization domain from AuthHub ${admId}: attributes, connectors, policies, then regenerates ALFA and Java dictionaries."
                dependsOn([preCheckDomainPullFrom] + domainPullFromDeps)
            }

            domainPullFromDeps.each { taskName ->
                project.tasks.named(taskName).configure { mustRunAfter(project.tasks."${preCheckDomainPullFrom}") }
            }
        } else {
            // we are adm, not hub


        project.tasks.register(pullFromAdm, AdmPullTask.class) {
            group specificGroup
            adm = lAdm
            domainFile = project.file("build/alfa/${group}/domain.yaml")
        }

        project.tasks.register(pushToAdm, AdmPushTask.class) {
            group specificGroup
            domainFile.set(project.tasks.buildAuthzDomain.domainFile)
            adm = lAdm
        }
        }
        project.tasks.register(spawnAdsWithAdm, RunSpawnedAds.class) {
            group 'other'
            adm = lAdm
            config = lAlfa
            classpath = project.configurations.adsRuntimeV2
            pipClasspath = project.configurations.pip

        }

        project.tasks.register(waitToStartAdsForAdm, WaitAdsStartedTask.class) {
            group 'other'
            adm = lAdm
        }

        project.tasks.register(runAdsWithAdm) {
            group specificGroup
            dependsOn waitToStartAdsForAdm
            description "Starts ADS with a remote domain from AuthHub on a random port and keep it running"
            doFirst {
                logger.lifecycle("ADS is running on port ${lAdm.adsHttpPort}. ${logger.isInfoEnabled()?"": "To see ADS output run gradle with --info. "}Stop it with Ctrl-C!")
            }
            doLast {
                def process = lAdm.process
                try {
                    if (process == null || false == logger.isInfoEnabled()) {
                        Thread.currentThread().sleep(72000000)
                    } else {
                        def line;
                        BufferedReader reader =
                                new BufferedReader(new InputStreamReader(process.getInputStream()));
                        while (process.isAlive() && (line = reader.readLine()) != null) {
                            logger.info(line)
                        }
                    }
                } catch (Exception e) {
                    logger.info("Out of ADS with exception",e)
                    if (lAdm.process != null) {
                        logger.info("Stopping ADS process")
                        lAdm.process.destroyForcibly()
                    }
                }
            }
        }
        project.tasks.register(testAdm, Test.class) {
            group specificGroup
            description "Spawns ADS with a remote domain from AuthHub and then run the tests"
            environment "ALFA_TEST_REMOTE_URL", "http://127.0.0.1:${() -> lAdm.adsHttpPort}/authorize"
            environment "ALFA_TEST_REMOTE_USER", "pdp-user"
            environment "ALFA_TEST_REMOTE_PASSWORD", "secret"

        }
        project.tasks.register(stopAdsForAdm, StopAdmAdsTask.class) {
            group 'other'
            adm = lAdm
            config = lAlfa
        }

        if (!isHub) {
            int admIndex = lAlfa.repositories.adms.findIndexOf { it.environment.equals(lAdm.environment) }
            project.logger.info("Index of ${lAdm.environment} is ${admIndex}")
            if (admIndex > 0) {
                lAlfa.repositories.adms.subList(0, admIndex).forEach(source -> {
                    String taskName = "${promoteFrom}${source.environment}To${lAdm.environment}"
                    String sourceTask = "${pullFrom}${source.environment}"
                    project.logger.info("  adding task ${taskName}. With source task ${sourceTask}")
                    project.tasks.register(taskName, AdmPushTask.class) {
                        group specificGroup
                        domainFile.set(project.tasks."${sourceTask}".domainFile)
                        adm = lAdm
                        dependsOn project.tasks."${sourceTask}"
                    }
                })

            }
        }


        if (!isHub) {
            project.tasks."${pushToAdm}".dependsOn(project.tasks.buildAuthzDomain)
        }
        project.tasks."${waitToStartAdsForAdm}".dependsOn(project.tasks."${spawnAdsWithAdm}")
        project.tasks."${spawnAdsWithAdm}".finalizedBy(project.tasks."${waitToStartAdsForAdm}")
        project.tasks."${testAdm}".dependsOn(project.tasks."${spawnAdsWithAdm}")
        project.tasks."${testAdm}".finalizedBy(project.tasks."${stopAdsForAdm}")

        if (isHub) {
            project.tasks.getByName(connectorPushTo).mustRunAfter(project.tasks.getByName(dictionaryPushTo))
            project.tasks.getByName(policyPushTo).mustRunAfter(project.tasks.getByName(connectorPushTo))

            project.tasks.getByName(connectorPullFrom).mustRunAfter(project.tasks.getByName(dictionaryPullFrom))
            project.tasks.getByName(policyPullFrom).mustRunAfter(project.tasks.getByName(connectorPullFrom))
            project.tasks.named(GENERATE_ALFA_DICTIONARY_TASK).configure { mustRunAfter(project.tasks.getByName(policyPullFrom)) }
            project.tasks.named(GENERATE_JAVA_DICTIONARY_TASK).configure { mustRunAfter(project.tasks.getByName(policyPullFrom)) }
        }
    }

    public class DomainRepositories {
        List<AdmExtension> adms = []

        void adm(Closure c) {
            AdmExtension adm = new AdmExtension(project, AlfaExtension.this)
            project.configure(adm, c);
            adm.repoType("asm")
            adms.add(adm);
        }
        void hub(Closure c) {
            AdmExtension adm = new AdmExtension(project, AlfaExtension.this)
            project.configure(adm, c);
            adms.add(adm);
        }
        void remote(Closure c) {
            AdmExtension adm = new AdmExtension(project, AlfaExtension.this)
            project.configure(adm, c);
            adms.add(adm);
        }


        @Override
        public String toString() {
            return "DomainRepositories{" +
                    "adms=" + adms +
                    '}';
        }
    }
}