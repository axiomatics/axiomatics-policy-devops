package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

import java.util.regex.Pattern

@CacheableTask
class CreateEnvTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection sources = project.objects.fileCollection()

    @OutputFile
    final RegularFileProperty outputFile = project.objects.fileProperty()

    @OutputFile
    File getInfoFile() {
        new File(outputFile.get().asFile.path + '.info')
    }

    // Matches ${VAR}, ${VAR:default}, ${VAR:-default} — group 1: name, group 2: default value (optional)
    private static final Pattern ENV_PATTERN =
            Pattern.compile(/\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-?([^}]*))?\}/)

    @TaskAction
    void execute() {
        // TreeMap for sorted output; value holds source files (ordered) and optional default value
        def envVars = new TreeMap<String, Map<String, Object>>()

        sources.asFileTree.each { File file ->
            if (file.name.endsWith('.alfa')) return
            def relativePath = project.relativePath(file)
            file.eachLine('UTF-8') { String line ->
                def matcher = ENV_PATTERN.matcher(line)
                while (matcher.find()) {
                    def varName = matcher.group(1)
                    def defaultVal = matcher.group(2)
                    if (!envVars.containsKey(varName)) {
                        envVars[varName] = [files: new LinkedHashSet<String>(), defaultValue: defaultVal]
                    } else if (defaultVal != null && envVars[varName].defaultValue == null) {
                        envVars[varName].defaultValue = defaultVal
                    }
                    envVars[varName].files.add(relativePath)
                }
            }
        }

        File output = outputFile.get().asFile
        output.parentFile.mkdirs()

        output.withWriter('UTF-8') { writer ->
            writer.writeLine("# See ${output.name}.info for more information")
            envVars.each { String var, Map info ->
                writer.writeLine("${var}=")
            }
        }

        infoFile.withWriter('UTF-8') { writer ->
            writer.writeLine('Environment variables extracted from deployment, configuration and attribute connector files.')
            writer.writeLine('Fill in the values in the .env file and load it using the appropriate command for your platform.')
            writer.writeLine('')
            writer.writeLine('Platform    Command')
            writer.writeLine('----------- --------------------------------------------------------------------------------')
            writer.writeLine('Docker      docker run --env-file .env <image>')
            writer.writeLine('Kubernetes  kubectl create secret generic access-decision-service --from-env-file=.env')
            writer.writeLine('            (then reference via envFrom in pod spec)')
            writer.writeLine("Linux       export \$(grep -v '^#' .env | xargs) && java -jar access-decision-service.jar")
            writer.writeLine("Windows     Get-Content .env | Where-Object { \$_ -notmatch '^#' -and \$_ -match '=' } |")
            writer.writeLine("            ForEach-Object { \$k,\$v = \$_ -split '=',2; [System.Environment]::SetEnvironmentVariable(\$k,\$v) }")
            writer.writeLine('            java -jar access-decision-service.jar')
            writer.writeLine('')
            writer.writeLine('Variables')
            writer.writeLine('---------')
            envVars.each { String var, Map info ->
                def fileList = info.files.join(', ')
                def defaultStr = info.defaultValue != null ? info.defaultValue : 'none'
                writer.writeLine('')
                writer.writeLine("${var}")
                writer.writeLine("  Referenced in: ${fileList}")
                writer.writeLine("  Default value: ${defaultStr}")
            }
            writer.writeLine('')
            writer.writeLine('')
            writer.writeLine('Kubernetes Secret')
            writer.writeLine('-----------------')
            writer.writeLine('Variables with a default value are commented out. Uncomment and set a value to override the default.')
            writer.writeLine('Move non-sensitive variables (hostnames, ports) to a ConfigMap if preferred.')
            writer.writeLine('')
            writer.writeLine('apiVersion: v1')
            writer.writeLine('kind: Secret')
            writer.writeLine('metadata:')
            writer.writeLine('  name: access-decision-service')
            writer.writeLine('type: Opaque')
            writer.writeLine('stringData:')
            envVars.each { String var, Map info ->
                def defaultVal = info.defaultValue
                if (defaultVal != null) {
                    writer.writeLine("#  ${var}:  # default: ${defaultVal}")
                } else {
                    writer.writeLine("  ${var}:")
                }
            }
        }

        logger.lifecycle("CreateEnv: wrote ${envVars.size()} variable(s) to ${output}")
    }
}
