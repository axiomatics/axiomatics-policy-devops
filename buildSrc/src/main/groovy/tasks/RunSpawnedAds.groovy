package tasks

import extensions.AdmExtension
import extensions.AlfaExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm

class RunSpawnedAds extends DefaultTask {


    @Input
    AlfaExtension config

    @Input
    AdmExtension adm

    @Input
    int admHash

    @InputFiles
    FileCollection classpath

    @InputFiles
    FileCollection pipClasspath


    @TaskAction
    void run() {
        if (config == null || adm == null) {
            throw new RuntimeException("Inputs can not be null")
        }
        String java = Jvm.current().getJavaExecutable().getAbsolutePath()
        logger.info("Starting ADS with domain from ${adm.environment}")
        logger.info("Java is ${java}")
        def cmds = new ArrayList<String>()
        logger.info("Classpath is ${classpath.asPath}")
        logger.info("Environment is ${adm.envVariable}")
        cmds << java

        if (System.getProperty("javax.net.ssl.trustStore") != null) {
            cmds << "-Djavax.net.ssl.trustStore=" + System.getProperty("javax.net.ssl.trustStore")
        }
        if (System.getProperty("javax.net.ssl.trustStorePassword") != null) {
            cmds << "-Djavax.net.ssl.trustStorePassword=" + System.getProperty("javax.net.ssl.trustStorePassword")
        }
        if (System.getProperty("javax.net.ssl.trustStoreType") != null) {
            cmds << "-Djavax.net.ssl.trustStoreType=" + System.getProperty("javax.net.ssl.trustStoreType")
        }

        // ADS v2 is a Spring Boot fat JAR (PropertiesLauncher) — no main class or server directive needed
        cmds << "-jar"
        cmds << classpath.singleFile.absolutePath

        if (adm.adsHttpPort == null) {
            ServerSocket socket = new ServerSocket(0)
            socket.close()
            int port = socket.getLocalPort()
            if (port < 1) {
                throw new RuntimeException("Could not get local port")
            }
            adm.adsHttpPort = port
            logger.info("Obtained free port ${port}")
        } else {
            logger.lifecycle("Port overridden: " + adm.adsHttpPort)
        }

        // Stage pip jars into lib/ next to the working dir so PropertiesLauncher picks them up
        def workDir = adm.project.buildDir.toPath().resolve(this.name)
        def libDir = workDir.resolve("lib")
        libDir.toFile().mkdirs()
        if (pipClasspath != null && !pipClasspath.isEmpty()) {
            pipClasspath.files.each { jar ->
                def dest = libDir.resolve(jar.name).toFile()
                if (!dest.exists()) {
                    logger.info("Staging pip jar: " + jar.name)
                    java.nio.file.Files.copy(jar.toPath(), dest.toPath())
                }
            }
        }

        def configFile = workDir.resolve("application.yml")
        def configFileAsString = configFile.toAbsolutePath().toString()
        logger.info("Config file is ${configFileAsString}")
        def configContent = getApplicationYaml()
        logger.info("Config content: ${configContent}")
        configFile << configContent

        cmds << "--spring.config.additional-location=file:" + configFileAsString
        cmds << "--server.port=" + adm.adsHttpPort

        ProcessBuilder builder = new ProcessBuilder(cmds)
        builder.directory(workDir.toFile())
        builder.environment() << adm.envVariable
        builder.redirectErrorStream(true)

        adm.process = builder.start()
    }

    private String getDomainUrl() {
        boolean isBasic = adm.basicCredentials != null
        def template
        if (isBasic) {
            template = adm.apiUrlPullStandaloneTemplate
        } else {
            template = adm.apiUrlPullAsmTemplate
        }
        return adm.host + String.format(template, adm.alfa.namespace, adm.domainName)
    }

    @Internal
    String getApplicationYaml() {
        def url = getDomainUrl()
        logger.lifecycle("Location of domain is ${url}")
        def tlsBundleName = hasTlsConfig() ? "admBundle" : null
        return """
license: file:${adm.project.getProjectDir()}/${config.licenseFile}

domain:
  path: ${url}
  authentication:
${getAuthenticationYaml(tlsBundleName)}

${getSpringYaml(tlsBundleName)}

logging:
  level:
    root: WARN
    org.apache.jcs: \${LOGLEVEL:-${logger.isInfoEnabled() ? "WARN" : "INFO"}}
    com.axiomatics: \${LOGLEVEL:-${logger.isInfoEnabled() ? "DEBUG" : "INFO"}}
"""
    }

    private boolean hasTlsConfig() {
        return System.getProperty("javax.net.ssl.trustStore") != null
    }

    String getAuthenticationYaml(String tlsBundleName) {
        def tls = tlsBundleName ? "\n    tlsConfigurationId: ${tlsBundleName}" : ""
        if (adm.basicCredentials != null) {
            return """    username: ${adm.basicCredentials.username}
    password: ${adm.basicCredentials.password}${tls}"""
        } else {
            return """    oauth2ClientId: adm-client${tls}"""
        }
    }

    String getSpringYaml(String tlsBundleName) {
        def securityParts = []
        def springParts = []

        // Always configure a local user for the test task to connect to ADS with basic auth
        securityParts << """    user:
      name: pdp-user
      password: secret"""

        if (adm.oidcCredentials != null) {
            securityParts << """    oauth2:
      client:
        registration:
          adm-client:
            client-id: ${adm.oidcCredentials.client_id}
            client-secret: ${adm.oidcCredentials.client_secret}
            scope: openid
            authorization-grant-type: client_credentials
            provider: adm-client
        provider:
          adm-client:
            token-uri: ${adm.host}${adm.oidcCredentials.token_uri}"""
        }

        springParts << "  security:\n" + securityParts.join("\n")

        if (tlsBundleName) {
            String file = System.getProperty("javax.net.ssl.trustStore")
            String password = System.getProperty("javax.net.ssl.trustStorePassword")
            String type = System.getProperty("javax.net.ssl.trustStoreType", "JKS")
            println("Truststore for ADS to ADM set: " + file)
            springParts << """  ssl:
    bundle:
      jks:
        ${tlsBundleName}:
          truststore:
            location: file:${file}
            password: ${password}
            type: ${type}"""
        }
        return "spring:\n" + springParts.join("\n")
    }
}
