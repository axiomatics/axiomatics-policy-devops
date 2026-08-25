package tasks


import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile

import java.util.function.Supplier

abstract class AdmPullTask extends AbstractAdmTask {


    @OutputFile
    abstract RegularFileProperty getDomainFile()


    protected String getMakeBody() {
        null
    }

    @Override
    protected String getRequestMethod() {
        "GET"
    }

    @Override
    protected String getAdmUrlTemplate() {
        adm.apiUrlPullStandaloneTemplate
    }

    @Override
    protected String getAsmAdmUrlTemplate() {
        adm.apiUrlPullAsmTemplate
    }

    @Override
    protected int getExpectedHttpReturnCode() {
        200
    }

    @Override
    protected void handleResposne(Supplier<String> response) {
        File domainFileValue = domainFile.get().asFile
        domainFileValue.withWriter("utf-8") {writer->
            writer.write(response.get())}
        logger.lifecycle("Domain ${getUrl()} stored to ${domainFileValue.getAbsolutePath()}")
    }
    protected String getServiceName() {
        return "ADM "+getRequestMethod();
    }
}