package tasks

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.yaml.snakeyaml.LoaderOptions

import java.util.function.Supplier

abstract class AdmPushTask extends AbstractAdmTask {


    private static final ObjectMapper objectMapper
    static {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(100 * 1024 * 1024); //Sets maximum YAML size to 100 MB
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .loaderOptions(loaderOptions)
                .build();
        objectMapper = new ObjectMapper(yamlFactory);
    }

    @InputFile
    abstract RegularFileProperty getDomainFile()

    @Internal
    String newDomainId

    protected String getMakeBody() {
        com.fasterxml.jackson.dataformat.yaml.YAMLFactory
        File yamlFile = domainFile.get().asFile
        logger.info("Source file is " + yamlFile.absolutePath);
        Object obj
        try (FileInputStream fis = new FileInputStream(yamlFile)) {
            obj = objectMapper.readValue(fis, Object.class);
        }
        com.fasterxml.jackson.databind.ObjectMapper jsonWriter = new com.fasterxml.jackson.databind.ObjectMapper(); //JSON ONLY
        jsonWriter.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    @Override
    protected String getRequestMethod() {
        "POST"
    }

    @Override
    protected String getAdmUrlTemplate() {
        adm.apiUrlPushStandaloneTemplate
    }

    @Override
    protected String getAsmAdmUrlTemplate() {
        adm.apiUrlPushAsmTemplate;
    }

    @Override
    protected int getExpectedHttpReturnCode() {
        201
    }

    @Override
    protected void handleResposne(Supplier<String> response) {
        newDomainId = new JsonSlurper().parseText(response.get()).domainId
        logger.lifecycle("Domain successfully pushed to ${getUrl()}. Server returned new id ${newDomainId}")
    }
    protected String getServiceName() {
        return "ADM "+getRequestMethod();
    }
}