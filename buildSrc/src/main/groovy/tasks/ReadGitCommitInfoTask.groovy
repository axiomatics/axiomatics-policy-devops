import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// Writes build/repositoryAndVersion and build/version - customer-facing output files that external
// CI/CD pipelines (outside this repo) read after the Gradle build to tag Docker/Buildah images.
// This is a public contract: do not rename, reformat, or remove these files or their content format.
@CacheableTask
abstract class ReadGitCommitInfoTask extends DefaultTask {

    @Input
    abstract Property<String> getDomainIdentity()

    @OutputFile
    abstract RegularFileProperty getRepositoryAndVersionFile()

    @OutputFile
    abstract RegularFileProperty getVersionFile()

    @TaskAction
    void execute() {
        String identity = domainIdentity.get()
        repositoryAndVersionFile.get().asFile.write(identity, "UTF-8")
        String shortRef = identity.length() >= 7 ? identity[-7..-1] : identity
        versionFile.get().asFile.write(shortRef, "UTF-8")
    }
}
