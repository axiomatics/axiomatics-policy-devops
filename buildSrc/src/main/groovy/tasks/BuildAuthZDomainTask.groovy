import com.axiomatics.domtool.Domain
import com.axiomatics.domtool.XacmlPolicyUtil
import com.axiomatics.domtool.cli.ConfigurableDomainCommand
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.yaml.snakeyaml.Yaml

import java.nio.charset.StandardCharsets
import java.nio.file.Path

@CacheableTask
abstract class BuildAuthZDomainTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    File srcDir

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree xmlPolicies

    @OutputFile
    abstract RegularFileProperty getDomainFile()

    @Input
    String mainPolicy

    @Input
    abstract Property<String> getDomainIdentity()

    @Input
    abstract Property<String> getGitCommitAuthor()

    @Input
    abstract Property<String> getGitCommitMessage()

    @Input
    abstract MapProperty<String, String> getExtraMetadata()


    @TaskAction
    execute() {
        File domainFileValue = domainFile.get().asFile

        def command = new ConfigurableDomainCommand();
        command.srcDir = srcDir;
        command.xmlPolicies = xmlPolicies;
        command.mainPolicy = mainPolicy;
        command.outputYamlFile = domainFileValue

        Map<String, String> metadataMap = new LinkedHashMap<>(extraMetadata.get())
        if (gitCommitAuthor.get()) metadataMap.put("Author", gitCommitAuthor.get())
        if (gitCommitMessage.get()) metadataMap.put("Commit-Message", gitCommitMessage.get())
        // ADM replaces the domain's identity with its own domain id; the metadata keeps it
        if (domainIdentity.get()) metadataMap.put("Git-Identity", domainIdentity.get())

        command.metadataYamlFile = createMetadataFile(srcDir.toPath().resolve("metadata.yaml"), metadataMap)
        command.domainIdentityGitHash = domainIdentity.get();
        logger.info("Set version to ${command.domainIdentityGitHash}")

        logger.info("Building authorization domain from ${srcDir} with ${xmlPolicies.size()} xacml policies. Main policy" +
                " is ${mainPolicy}. Domain identity: ${ command.domainIdentityGitHash}. Output to ${domainFileValue.getAbsolutePath()}")
        command.call();
        checkMainPolicy(command.getResult(), domainFileValue)
    }

    /**
     * Fails the build when the main policy is not a top-level policy or policy set of the built domain (the same rule as
     * the tests' local PDP). domtool writes any main policy it is given; ADS would only fail when loading the domain.
     * The ALFA compiler writes one XACML document per top-level policy, so each document's root id is one of them.
     */
    private static void checkMainPolicy(Domain domain, File domainFile) {
        if (domain?.policy == null) {
            throw new GradleException("Internal APD error: domtool returned no domain for ${domainFile}")
        }
        String mainPolicyId = domain.policy.mainPolicyId
        List<String> topLevel = (domain.policy.xacmlSpecifications ?: []).collect { XacmlPolicyUtil.getPolicyIdentifier(it) }
        if (!topLevel.contains(mainPolicyId)) {
            domainFile.delete()
            String found = topLevel.findAll().collect { "'${withoutAlfaPrefix(it)}'" }.sort().join(",")
            throw new GradleException("Main policy ${withoutAlfaPrefix(mainPolicyId)} not found. Found policies ${found ?: 'none'}." +
                    " Set the main policy with alfa { mainpolicy } in build.gradle.")
        }
    }

    private static String withoutAlfaPrefix(String id) {
        String prefix = ConfigurableDomainCommand.MAIN_POLICY_PREFIX
        id?.startsWith(prefix) ? id.substring(prefix.length()) : id
    }

    def Path createMetadataFile(Path srcMetadataYaml, Map<String, String> inProcessMetaDataMap) {
        Map<String,String> metadataMap = new HashMap();
        logger.info("InProcessMetaDataMap contains ${inProcessMetaDataMap.entrySet().size()} entries; " + inProcessMetaDataMap.inspect())
        metadataMap.putAll(inProcessMetaDataMap)

        if (srcMetadataYaml != null && srcMetadataYaml.toFile().canRead()) {
            try {
                logger.info("Reading metadata from " + srcMetadataYaml)
                ObjectMapper objectMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()); //3 mb limit ok, just metadata yaml
                File metaDataFile = srcMetadataYaml.toFile();

                    try {
                        Map<String, Map<String, String>> srcMetadataMap = objectMapper.readValue(metaDataFile, Map.class);
                        logger.info("Got " + srcMetadataMap.size() + " entries from " + srcMetadataYaml);
                        metadataMap.putAll(srcMetadataMap)
                    } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
                        logger.info("No entries to map found in " + metaDataFile +". Content :" + metaDataFile.text + ". Exception below only information:",e);
                    }


            } catch (Exception e) {
                throw new RuntimeException("Failed to parse metadata file at " + srcMetadataYaml,e);
            }
        } else {
            // NOTE: srcMetadataYaml is a java.nio.file.Path, which implements Iterable<Path> - so
            // Groovy's `+` operator on it resolves to list-concatenation (DefaultGroovyMethods'
            // Iterable#plus), not string concatenation, and would throw a MissingMethodException
            // out of logger.info(Object) here. Use a GString instead to force a real toString().
            logger.info("${srcMetadataYaml} is null or not readable.");
        }
        File mapFile = File.createTempFile("alfa-domain-metadata-", ".yaml")
        mapFile.deleteOnExit()
        try {
            Yaml yaml = new Yaml();
            final FileWriter writer = new FileWriter(mapFile, StandardCharsets.UTF_8, false);
            yaml.dump(metadataMap, writer);
            logger.info("Wrote metadatamap to " + mapFile + ": ${mapFile.readLines()} ")
        } catch (Exception e) {
            throw new RuntimeException("Failed to write metadata file to " + mapFile,e);
        }
        return mapFile.toPath()
    }
}

