package tasks

import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import remote.AdsApplicationYaml
import remote.RemoteConnection

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Starts ADS in the background with the domain domainName of a remote's ADM (spawnAdsWith...). ADS writes its output to
 * {@link #getAdsLogFile()}; the process and its HTTP port are kept on the remote ({@code AdmExtension.process},
 * {@code adsHttpPort}) for the tasks that wait for, use and stop it. The output is a running server, so never
 * up-to-date.
 */
abstract class RunSpawnedAds extends DefaultTask {

    @Internal
    AdmExtension adm

    /** The ADS fat jar. */
    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    /** Jars ADS loads from lib/: attribute connectors and the project's own jar. */
    @Classpath
    abstract ConfigurableFileCollection getPipClasspath()

    @Internal
    abstract RegularFileProperty getLicenseFile()

    @Internal
    abstract DirectoryProperty getWorkDir()

    @Internal
    abstract RegularFileProperty getAdsLogFile()

    RunSpawnedAds() {
        outputs.upToDateWhen { false }
        workDir.convention(project.layout.buildDirectory.dir(name))
        adsLogFile.convention(workDir.file("ads.log"))
    }

    @TaskAction
    void run() {
        RemoteConnection remote = RemoteConnection.of(adm)
        File dir = workDir.get().asFile
        File libDir = new File(dir, "lib")
        libDir.deleteDir()
        libDir.mkdirs()
        pipClasspath.files.each { File jar ->
            logger.info("Staging for ADS: ${jar.name}")
            Files.copy(jar.toPath(), new File(libDir, jar.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        String domainUrl = remote.withClient { client -> remote.domainManager(client).getDomainDocumentUrl(remote.domainName) }
        logger.lifecycle("Starting ADS with domain ${domainUrl} of ${remote.label}")
        File configFile = new File(dir, "application.yml")
        configFile.text = AdsApplicationYaml.of(remote, domainUrl, licenseFile.get().asFile.absolutePath, logger.infoEnabled).render()
        logger.info("ADS configuration: ${configFile}")

        if (adm.adsHttpPort == null) {
            adm.adsHttpPort = freePort()
            logger.info("Obtained free port ${adm.adsHttpPort}")
        } else {
            logger.lifecycle("Port overridden: " + adm.adsHttpPort)
        }

        List<String> command = [Jvm.current().javaExecutable.absolutePath]
        ["javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreType"].each { String key ->
            if (System.getProperty(key) != null) {
                command << "-D${key}=${System.getProperty(key)}".toString()
            }
        }
        // ADS v2 is a Spring Boot fat JAR (PropertiesLauncher): it loads lib/ of the working directory
        command << "-jar" << classpath.singleFile.absolutePath
        command << "--spring.config.additional-location=file:${configFile.absolutePath}".toString()
        command << "--server.port=${adm.adsHttpPort}".toString()

        File logFile = adsLogFile.get().asFile
        logger.info("ADS output: ${logFile}")
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
        builder.environment().putAll(adm.envVariable.collectEntries { k, v -> [(k): String.valueOf(v)] } as Map<String, String>)
        adm.process = builder.start()
    }

    private static String freePort() {
        new ServerSocket(0).withCloseable { it.localPort.toString() }
    }
}
