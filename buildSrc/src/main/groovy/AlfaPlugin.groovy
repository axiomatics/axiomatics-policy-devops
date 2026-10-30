import extensions.AlfaExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import tasks.AdmPushTask
import tasks.AlfaCompilationTask
import tasks.CheckAuthzDomainUpgradeTask
import tasks.UpgradeAuthzDomainTask
import tasks.CreateEnvTask
import tasks.PrintHubConnectionsTask
import tasks.dictionary.GenerateAlfaDictionary
import tasks.dictionary.GenerateJavaDictionary
import tasks.dictionary.GenerateYamlDictionary
import org.gradle.jvm.toolchain.JavaLanguageVersion
import upgrade.AuthzDomainUpgradeNotice
import upgrade.AuthzDomainUpgrades

import javax.inject.Inject
import java.nio.file.Files

abstract class AlfaPlugin implements Plugin<Project> {

    @Inject
    abstract FlowScope getFlowScope()

    @Inject
    abstract FlowProviders getFlowProviders()


    void apply(Project project) {
        project.logger.info("Apply alfa plugin")
        def accessKey = System.env.AXIOMATICS_ACCESS_KEY_ID ?: project.findProperty('AXIOMATICS_ACCESS_KEY_ID')
        def secretKey = System.env.AXIOMATICS_SECRET_ACCESS_KEY ?: project.findProperty('AXIOMATICS_SECRET_ACCESS_KEY')
        if (accessKey == null || secretKey == null ||
                accessKey.equals("Put the key id provided by Axiomatics here!") ||
                secretKey.equals("Put the secret key provided by Axiomatics here!")
        ) {
            throw new GradleException("Credential to Axiomatics repository not set. Set AXIOMATICS_ACCESS_KEY_ID and AXIOMATICS_SECRET_ACCESS_KEY " +
                    "as environment variables or in ~/.gradle/gradle.properties. " +
                    "If you have not received the credentials, contact your Axiomatics support")
        }
        project.logger.info("Access key id found")
        boolean allowUnsafeRequested = project.findProperty('apd.allowUnsafe') == 'true'
        project.extensions.create('alfa', AlfaExtension, project)
        project.plugins.apply('distribution')
        project.getPluginManager().apply("com.palantir.docker")
        project.tasks.register("buildAuthzDomain", BuildAuthZDomainTask.class) {
            group "axiomatics"
            domainIdentity = project.extensions.alfa.domainIdentity
            gitCommitAuthor = project.extensions.alfa.gitCommitAuthor
            gitCommitMessage = project.extensions.alfa.gitCommitMessage
            extraMetadata.set(project.provider { project.extensions.alfa.metadata })
        }
        project.tasks.register("readGitCommitInfo", ReadGitCommitInfoTask.class) {
            domainIdentity = project.extensions.alfa.domainIdentity
            repositoryAndVersionFile = project.file("${project.buildDir}/repositoryAndVersion")
            versionFile = project.file("${project.buildDir}/version")
        }
        project.tasks.register("compileAlfa", AlfaCompilationTask.class) {
            group "axiomatics"
        }
        project.tasks.register(AlfaExtension.GENERATE_ALFA_DICTIONARY_TASK, GenerateAlfaDictionary) {
            group "axiomatics-dictionary"
            description "Generates the ALFA attribute dictionary (.alfa) from the YAML attribute definition file."
            inputFile = project.extensions.alfa.attributesYamlFile
            outputFile = project.extensions.alfa.attributesAlfaFile
            allowUnsafe = allowUnsafeRequested
        }
        project.tasks.register("generateYamlDictionaryFromAlfa", GenerateYamlDictionary) {
            group "axiomatics-dictionary"
            description "Generates the YAML attribute dictionary from the ALFA attribute specification file."
            inputFile = project.extensions.alfa.attributesAlfaFile
            outputFile = project.extensions.alfa.attributesYamlFile
            allowUnsafe = allowUnsafeRequested
        }
        project.tasks.register(AlfaExtension.GENERATE_JAVA_DICTIONARY_TASK, GenerateJavaDictionary) {
            group "axiomatics-dictionary"
            description "Generates the Java attribute Dictionary class from the YAML attribute definition file."
            inputFile  = project.extensions.alfa.attributesYamlFile
            outputFile = project.extensions.alfa.dictionaryJavaFile
            allowUnsafe = allowUnsafeRequested
            commitRef = project.extensions.alfa.commitRef
            commitDate = project.extensions.alfa.commitDate
        }
        // the generated dictionaries are sources: when generated in the same build, generate before compiling
        project.tasks.named("compileAlfa") { mustRunAfter AlfaExtension.GENERATE_ALFA_DICTIONARY_TASK }
        project.tasks.named("buildAuthzDomain") { mustRunAfter AlfaExtension.GENERATE_ALFA_DICTIONARY_TASK }
        project.tasks.matching { it.name == "compileTestJava" }.configureEach {
            mustRunAfter AlfaExtension.GENERATE_JAVA_DICTIONARY_TASK
        }
        project.tasks.register(AuthzDomainUpgrades.CHECK_TASK, CheckAuthzDomainUpgradeTask) {
            description "Reports authorization domain content that can be upgraded to a newer format. The same report is shown at the end of every successful build."
            authzDomainDir = project.layout.projectDirectory.dir(project.provider { project.extensions.alfa.srcDir })
        }
        // Shown at the end of every successful build, whatever tasks were invoked
        flowScope.always(AuthzDomainUpgradeNotice) {
            parameters.buildFailed = flowProviders.buildWorkResult.map { it.failure.present }
            parameters.authzDomainDir = project.provider { project.file(project.extensions.alfa.srcDir).absolutePath }
        }
        project.tasks.register(AuthzDomainUpgrades.UPGRADE_TASK, UpgradeAuthzDomainTask) {
            group "axiomatics"
            description "Upgrades authorization domain content to newer formats. Requires all files to be committed."
            authzDomainDir = project.layout.projectDirectory.dir(project.provider { project.extensions.alfa.srcDir })
            projectDir = project.projectDir
            allowUnsafe = allowUnsafeRequested
            // the upgrade code runs in a separate JVM on the APD bundle, already resolved for the tests
            upgradeClasspath.from(project.configurations.named("testRuntimeClasspath"))
        }
        project.tasks.register("printHubConnections", PrintHubConnectionsTask) {
            group "axiomatics"
            description "Prints, for every remote, how an ADS loads its domain (client id, token URI, domain URL), and the identity and metadata of the domain."
            alfa = project.extensions.alfa
        }
        project.tasks.register("createEnvFile", CreateEnvTask) {
            sources.from(
                project.layout.projectDirectory.file('deployment.yaml'),
                project.fileTree('src/authorizationDomain') { exclude '**/*.alfa' },
                project.fileTree('src/extra')
            )
        }
        project.distributions {
            main {
                contents {

                    into('domain') {
                        exclude("README.md")
                        from 'src/extra'
                    }
                }
            }
        }
        project.configurations {
            compileOnly.extendsFrom adsCompile
            implementation.extendsFrom pip
            ads.extendsFrom adsCompile
        }
        project.dependencies {
            ads project.tasks.jar.outputs.files
        }
        project.sourceSets {
            main {
                resources {
                    srcDir 'extra'
                }
            }
        }
        project.afterEvaluate {
            project.logger.info("Project after evaluate begins")
            AlfaExtension alfa = project.extensions.alfa
            def srcFile = "${it.rootDir}/${project.extensions.alfa.deploymentDescriptor}"
            if (! new File(srcFile).exists()) {
                throw new GradleException("alfa.deploymentdescriptor does not exist: ${srcFile}")
            }
            project.tasks.stageDeploymentDescriptor {
                from srcFile
            }
            project.tasks.withType(AlfaCompilationTask.class) {
                extraAttributeIdCheck = alfa.extraAttributeIdCheck
            }
            project.tasks.withType(Test.class) {
                doFirst {
                    logger.info("Environment is ${environment}")
                    workingDir "src/extra"
                    if (!workingDir.exists()) {
                        Files.createDirectories(workingDir.toPath())
                    }
                }
                useJUnitPlatform()
                outputs.upToDateWhen {false}
                environment "ALFA_EXTRA_ATTRIBUTE_ID_CHECK" , "${project.extensions.alfa.extraAttributeIdCheck}"
                environment "ALFA_TEST_REMOTE_MAIN_POLICY" , "${project.extensions.alfa.mainpolicy}"
                if (project.extensions.alfa.withVisualTrace != null) {
                    project.logger.info("Setting ALFA_WITH_VISUAL_TRACE to ${project.extensions.alfa.withVisualTrace}" )
                    environment "ALFA_WITH_VISUAL_TRACE" , "${project.extensions.alfa.withVisualTrace}"
                }
                testLogging {
                    events TestLogEvent.FAILED,
                            TestLogEvent.SKIPPED,
                            TestLogEvent.PASSED,
                            TestLogEvent.STANDARD_ERROR,
                            TestLogEvent.STANDARD_OUT
                    exceptionFormat TestExceptionFormat.FULL
                    showCauses true
                    showExceptions true
                    showStackTraces true

                    showStandardStreams true
                }
            }
            project.tasks.compileAlfa {
                // Resolved once here, at configuration time, into a plain value — doFirst runs at
                // execution time, where touching the live `project`/`Configuration` object (instead
                // of this already-resolved, serializable Set<File>) would be a config-cache violation.
                Set<File> policyFiles = project.configurations.policy.files
                doFirst {
                    if (!policyFiles.isEmpty()) {
                        logger.info("Policy dependencies on configuration 'policy': ${policyFiles}")
                    }
                }
                inputs.files(project.configurations.policy)
                        .withNormalizer(ClasspathNormalizer)
                        .withPropertyName("policy")
                inputs.files(project.fileTree("src/authorizationDomain/alfaSpecifications"))
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                        .withPropertyName("alfaSpecifications")
                outputs.dir(project.file("build/alfa/domain/xacmlSpecifications"))
                        .withPropertyName("xacmlSpecifications")
            }
            project.tasks.buildAuthzDomain {
                xmlPolicies = project.fileTree("${project.buildDir}/alfa/domain/xacmlSpecifications")
                srcDir = project.file(project.extensions.alfa.srcDir)
                mainPolicy  project.extensions.alfa.mainpolicy
                domainFile = project.file("${project.buildDir}/alfa/domain/ads/domain.yaml")
            }

            project.extensions.distributions.main {
                contents {
                    //this is ADS v1 distribution
                    into ('lib')    { from  project.configurations.adsRuntimeV1  }
                    into ('domain') { from  project.buildAuthzDomain,
                            alfa.licenseFile,
                            alfa.deploymentDescriptor
                    }

                }
            }

            def docker = project.extensions.docker
            docker.files project.installDist.outputs
            docker.name  project.extensions.alfa.dockerName == null ? project.name.toLowerCase() : project.extensions.alfa.dockerName.toLowerCase()

            project.tasks.stageAdsFiles.dependsOn project.tasks.buildAuthzDomain
            project.tasks.buildAuthzDomain.dependsOn project.tasks.compileAlfa
            project.tasks.buildAuthzDomain.dependsOn project.tasks.readGitCommitInfo
            project.tasks.dockerPrepare.dependsOn project.tasks.installDist
            project.tasks.buildAdsDockerImage.dependsOn project.tasks.docker

            project.tasks.withType(AdmPushTask.class) {
                domainFile.convention(project.tasks.buildAuthzDomain.domainFile)
            }

            project.tasks.withType(Test) {
                File xacmlOutputDir = project.tasks.named("compileAlfa").get().outputs.files.singleFile
                logger.info("Setting XACML_XML_DIR_BY_GRADLE_TASK to ${xacmlOutputDir}")
                it.dependsOn project.tasks.named("compileAlfa")
                it.environment.put("XACML_XML_DIR_BY_GRADLE_TASK", xacmlOutputDir.getAbsolutePath())
            }

            project.tasks.register("stageDeployment", DefaultTask.class) {
                group "axiomatics"
                doFirst {
                    def file = project.tasks.installDeploymentDist.outputs.getFiles().getFiles().first()
                    println "Your ADS deployment has been staged to $file"
                }
            }
            project.tasks.stageDeployment.dependsOn project.tasks.installDeploymentDist


            project.logger.info("Project after evaluate ends")
        } //after evaluate
        project.logger.info("Apply alfa plugin ends")
    }
}