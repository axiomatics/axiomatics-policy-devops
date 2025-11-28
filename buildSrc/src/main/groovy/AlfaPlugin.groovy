import extensions.AlfaExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import tasks.AdmPushTask
import tasks.AlfaCompilationTask
import org.gradle.jvm.toolchain.JavaLanguageVersion

import java.nio.file.Files

class AlfaPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.logger.info("Apply alfa plugin")
        def accessKey = System.env.AXIOMATICS_ACCESS_KEY_ID ?: project.findProperty('AXIOMATICS_ACCESS_KEY_ID')
        def secretKey = System.env.AXIOMATICS_SECRET_ACCESS_KEY ?: project.findProperty('AXIOMATICS_SECRET_ACCESS_KEY')
        if (accessKey == null || secretKey == null ||
                accessKey.equals("Put the key id provided by Axiomatics here!") ||
                secretKey.equals("Put the secret key provided by Axiomatics here!")
        ) {
            throw new GradleException("Credential to Axiomatics repository not set. Please set credentials provided by Axiomatics in file gradle.properties or as environment variables. " +
                    "If you have not received the credentials, contact your Axiomatics support")
        }
        project.logger.info("Access key id found ${accessKey}")
        project.extensions.create('alfa', AlfaExtension, project)
        project.plugins.apply('distribution')
        project.getPluginManager().apply("com.palantir.docker")
        project.tasks.register("buildAuthzDomain", BuildAuthZDomainTask.class) {
            group "axiomatics"
        }
        project.tasks.register("compileAlfa", AlfaCompilationTask.class) {
            group "axiomatics"
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
                doFirst {
                    logger.info("Policy dependnecies on configuration 'policy' is ${project.configurations.policy}")
                }
                inputs.files project.configurations.policy
                inputs.files project.fileTree("src/authorizationDomain/alfaSpecifications")
                outputs.dir project.file("build/alfa/domain/xacmlSpecifications")
            }
            project.tasks.buildAuthzDomain {
                xmlPolicies = project.fileTree("${project.buildDir}/alfa/domain/xacmlSpecifications")
                srcDir = project.file(project.extensions.alfa.srcDir)
                mainPolicy  project.extensions.alfa.mainpolicy
                domainFile = project.file("${project.buildDir}/alfa/domain/ads/domain.yaml")
                versionFile = project.buildDir.toPath().resolve("repositoryAndVersion").toFile()

            }

            project.extensions.distributions.main {
                contents {
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
            project.tasks.dockerPrepare.dependsOn project.tasks.installDist
            project.tasks.buildAdsDockerImage.dependsOn project.tasks.docker
            project.tasks.buildAuthzDomain.dependsOn project.tasks.readGitCommitInfo

            project.tasks.withType(AdmPushTask.class) {
              if (domainFile == null) {
                  domainFile project.tasks.buildAuthzDomain.domainFile
              }
            }

            project.tasks.withType(Test) {
                File xacmlOutputDir = project.tasks.named("compileAlfa").get().outputs.files.singleFile
                logger.info("Setting XACML_XML_DIR_BY_GRADLE_TASK to ${xacmlOutputDir}")
                it.dependsOn project.tasks.named("compileAlfa")
                it.environment.put("XACML_XML_DIR_BY_GRADLE_TASK", xacmlOutputDir.getAbsolutePath())
            }

            project.logger.info("Project after evaluate ends")
        } //after evaluate
        project.logger.info("Apply alfa plugin ends")
    }
}