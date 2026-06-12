package tasks.dictionary

import tasks.GitOutputGuard
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

@CacheableTask
class GenerateYamlDictionary extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    final RegularFileProperty inputFile = project.objects.fileProperty()

    @OutputFile
    final RegularFileProperty outputFile = project.objects.fileProperty()

    private static final Map<String, String> CATEGORY_MAP = [
        'subjectCat'    : 'AccessSubject',
        'resourceCat'   : 'Resource',
        'actionCat'     : 'Action',
        'environmentCat': 'Environment',
    ]

    @TaskAction
    void execute() {
        File out = outputFile.get().asFile
        GitOutputGuard.check(this, out)
        out.parentFile.mkdirs()

        List<Map> attributes = parseAlfa(inputFile.get().asFile.getText('UTF-8')).sort { it.yamlKey }

        out.withWriter('UTF-8') { writer ->
            attributes.each { Map attr ->
                writer.writeLine("${attr.yamlKey}:")
                writer.writeLine("  xacmlId: ${attr.id}")
                writer.writeLine("  category: ${attr.category}")
                writer.writeLine("  datatype: ${attr.type}")
            }
        }

        logger.lifecycle("GenerateYamlDictionary: wrote ${attributes.size()} attribute(s) to ${out}")
    }

    private static List<Map> parseAlfa(String alfaContent) {
        List<Map> attributes = []
        int depth = 0
        Map currentAttr = null
        int attrOpenDepth = -1
        // Each entry: [name: <ns-token>, openDepth: <depth-after-opening-brace>]
        List<Map> namespaceStack = []

        alfaContent.eachLine { String line ->
            String trimmed = line.trim()
            if (trimmed.startsWith('//') || trimmed.isEmpty()) return

            int opens = trimmed.count('{')
            int closes = trimmed.count('}')

            // Push namespace when its opening brace is on this line
            def nsM = (trimmed =~ /^namespace\s+([\w.]+)/)
            if (nsM && opens > 0) {
                namespaceStack << [name: nsM[0][1], openDepth: depth + opens]
            }

            // Start attribute when its opening brace is on this line
            def attrM = (trimmed =~ /^attribute\s+(\w+)/)
            if (attrM && currentAttr == null && opens > 0) {
                currentAttr = [name: attrM[0][1]]
                attrOpenDepth = depth + opens
            }

            // Capture attribute body fields (depth before this line equals the attribute's open-depth)
            if (currentAttr != null && depth == attrOpenDepth) {
                def catM  = (trimmed =~ /^category\s*=\s*(\w+)/)
                def idM   = (trimmed =~ /^id\s*=\s*"([^"]+)"/)
                def typeM = (trimmed =~ /^type\s*=\s*(\w+)/)
                if (catM)  currentAttr.category = CATEGORY_MAP[catM[0][1]] ?: catM[0][1]
                if (idM)   currentAttr.id       = idM[0][1]
                if (typeM) currentAttr.type     = typeM[0][1]
            }

            depth += opens - closes

            // Attribute block just closed
            if (currentAttr != null && depth < attrOpenDepth) {
                if (currentAttr.id && currentAttr.category && currentAttr.type) {
                    String ns = namespaceStack.collect { it.name }.join('.')
                    String yamlKey = ns ? "${ns}.${currentAttr.name}" : currentAttr.name
                    attributes << [yamlKey: yamlKey, id: currentAttr.id, category: currentAttr.category, type: currentAttr.type]
                }
                currentAttr = null
                attrOpenDepth = -1
            }

            // Pop namespace blocks whose opening depth is now beyond current depth
            namespaceStack.removeAll { it.openDepth > depth }
        }

        return attributes
    }
}
