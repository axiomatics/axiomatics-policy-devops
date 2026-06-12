package tasks.dictionary

import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.yaml.snakeyaml.Yaml

@CacheableTask
class GenerateAlfaDictionary extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    final RegularFileProperty inputFile = project.objects.fileProperty()

    @OutputFile
    final RegularFileProperty outputFile = project.objects.fileProperty()

    private static final Map<String, String> CATEGORY_MAP = [
        'AccessSubject': 'subjectCat',
        'Resource'     : 'resourceCat',
        'Action'       : 'actionCat',
        'Environment'  : 'environmentCat',
    ]

    @TaskAction
    void execute() {
        File out = outputFile.get().asFile
        GitOutputGuard.check(this, out)
        out.parentFile.mkdirs()

        def yaml = new Yaml()
        Map<String, Map> attributes = yaml.load(inputFile.get().asFile.newInputStream())

        // Group by namespace (YAML key minus last dot-segment = attribute name)
        def byNamespace = new LinkedHashMap<String, List<Map>>()
        attributes.each { String key, Map attr ->
            String xacmlId = attr.xacmlId ?: key
            int dot = key.lastIndexOf('.')
            String ns = dot > 0 ? key.substring(0, dot) : key
            String localName = dot > 0 ? key.substring(dot + 1) : key
            byNamespace.computeIfAbsent(ns) { [] } << [
                name    : localName,
                id      : xacmlId,
                category: attr.category,
                type    : attr.datatype,
            ]
        }
        byNamespace = byNamespace.sort { a, b -> a.key <=> b.key }
        byNamespace.each { ns, attrs -> attrs.sort { it.name } }

        out.withWriter('UTF-8') { writer ->
            byNamespace.each { String ns, List<Map> attrs ->
                writer.writeLine("namespace ${ns} {")
                attrs.each { Map a ->
                    String cat = CATEGORY_MAP[a.category] ?: a.category
                    writer.writeLine("    attribute ${a.name} {")
                    writer.writeLine("        category = ${cat}")
                    writer.writeLine("        id = \"${a.id}\"")
                    writer.writeLine("        type = ${a.type}")
                    writer.writeLine("    }")
                    writer.writeLine('')
                }
                writer.writeLine('}')
                writer.writeLine('')
            }
        }

        logger.lifecycle("GenerateAlfaDictionary: wrote ${attributes.size()} attribute(s) across ${byNamespace.size()} namespace(s) to ${out}")
    }
}
