package extensions

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import tasks.*

class AlfaExtension {

    String namespace
    String mainpolicy = null
    String deploymentDescriptor = "deployment.yaml"
    String srcDir = "src/authorizationDomain"
    String licenseFile = "license/axiomatics_ADS.license"
    String dockerName
    boolean extraAttributeIdCheck = true
    Project project
    Map<String,String> metadata = new HashMap<>()
    String domainIdentity
    Boolean withVisualTrace = true

    DomainRepositories repositories = new DomainRepositories();

    AlfaExtension(Project project) {
        this.project = project;
        namespace = this.project.getRootProject().getName().replaceAll("-", "_").replaceAll("\\.", "_")
    }

    void repositories(Closure c) {
        project.configure(repositories, c);
        repositories.adms.forEach(adm -> addAdmTasksFor(adm, this))
    }

    void namespace(String spec) {
        this.namespace = spec
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

    def addAdmTasksFor = { AdmExtension lAdm, AlfaExtension lAlfa ->
        {
        }
        project.logger.info("Registring tasks for adm with environment ${lAdm.environment}")
        String admId = lAdm.environment
        String specificGroup = "axiomatics-adm-${lAdm.environment.toLowerCase()}"
        String pullFrom ="pullFrom"
        String promoteFrom = "promoteFrom"
        String pullFromAdm = pullFrom + admId
        String pushToAdm = "pushTo" + admId
        String spawnAdsWithAdm = "spawnAdsWith" + admId
        String runAdsWithAdm = "runAdsWith" + admId
        String waitToStartAdsForAdm = "waitToStartAds" + admId
        String testAdm = "test" + admId
        String stopAdsForAdm = "stopAdsFor" + admId

        project.tasks.register(pullFromAdm, AdmPullTask.class) {
            group specificGroup
            adm = lAdm
            admHash = lAdm.hashCode()
            domainFile = project.file("build/alfa/${group}/domain.yaml")
        }

        project.tasks.register(pushToAdm, AdmPushTask.class) {
            group specificGroup
            domainFile project.tasks.buildAuthzDomain.domainFile
            adm = lAdm
            admHash = lAdm.hashCode()
        }

        project.tasks.register(spawnAdsWithAdm, RunSpawnedAds.class) {
            group 'other'
            adm = lAdm
            config = lAlfa
            admHash = lAdm.hashCode()
            classpath = project.configurations.adsRuntimeV1

        }

        project.tasks.register(waitToStartAdsForAdm, WaitAdsStartedTask.class) {
            group 'other'
            adm = lAdm
            admHash = lAdm.hashCode()
        }

        project.tasks.register(runAdsWithAdm) {
            group specificGroup
            dependsOn waitToStartAdsForAdm
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
            environment "ALFA_TEST_REMOTE_URL", "http://127.0.0.1:${() -> lAdm.adsHttpPort}/authorize"
            environment "ALFA_TEST_REMOTE_USER", "pdp-user"
            environment "ALFA_TEST_REMOTE_PASSWORD", "secret"

        }
        project.tasks.register(stopAdsForAdm, StopAdmAdsTask.class) {
            group 'other'
            adm = lAdm
            config = lAlfa
            admHash = lAdm.hashCode()
        }

        int admIndex = lAlfa.repositories.adms.findIndexOf { it.environment.equals(lAdm.environment) }
        project.logger.info("Index of ${lAdm.environment} is ${admIndex}")
        if (admIndex > 0) {
            lAlfa.repositories.adms.subList(0, admIndex).forEach(source -> {
                String taskName = "${promoteFrom}${source.environment}To${lAdm.environment}"
                String sourceTask = "${pullFrom}${source.environment}"
                project.logger.info("  adding task ${taskName}. With source task ${sourceTask}")
                project.tasks.register(taskName, AdmPushTask.class) {
                    group specificGroup
                    domainFile project.tasks."${sourceTask}".domainFile
                    adm = lAdm
                    admHash = lAdm.hashCode() + source.hashCode()
                    dependsOn project.tasks."${sourceTask}"
                }
            })

        }


        project.tasks."${pushToAdm}".dependsOn(project.tasks.buildAuthzDomain)
        project.tasks."${waitToStartAdsForAdm}".dependsOn(project.tasks."${spawnAdsWithAdm}")
        project.tasks."${spawnAdsWithAdm}".finalizedBy(project.tasks."${waitToStartAdsForAdm}")
        project.tasks."${testAdm}".dependsOn(project.tasks."${spawnAdsWithAdm}")
        project.tasks."${testAdm}".finalizedBy(project.tasks."${stopAdsForAdm}")
    }

    public class DomainRepositories {
        List<AdmExtension> adms = []

        void adm(Closure c) {
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