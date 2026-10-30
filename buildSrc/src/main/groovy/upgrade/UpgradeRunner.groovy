package upgrade

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations

import java.nio.charset.StandardCharsets

/**
 * Runs an upgrade entry point in a separate JVM on the project's test runtime classpath, i.e. the APD bundle.
 * <p>
 * The upgrade code needs the attribute connector library and its dependencies. They are in the bundle, which every
 * project (also offline ones) already resolves to run its tests, but they are deliberately not on the Gradle build
 * (buildSrc) classpath.
 */
class UpgradeRunner {

    private final ExecOperations execOperations
    private final FileCollection classpath

    UpgradeRunner(ExecOperations execOperations, FileCollection classpath) {
        this.execOperations = execOperations
        this.classpath = classpath
    }

    /** Runs the main class and returns the non-blank lines it printed. Fails with its error output on failure. */
    List<String> run(String mainClassName, List<String> arguments) {
        def out = new ByteArrayOutputStream()
        def err = new ByteArrayOutputStream()
        // Local names must not clash with JavaExecSpec properties: Gradle resolves names in this closure on the spec first
        FileCollection upgradeClasspath = this.classpath
        def result = execOperations.javaexec { spec ->
            spec.setClasspath(upgradeClasspath)
            spec.mainClass.set(mainClassName)
            spec.setArgs(arguments)
            spec.setStandardOutput(out)
            spec.setErrorOutput(err)
            spec.setIgnoreExitValue(true)
        }
        if (result.exitValue != 0) {
            String error = err.toString(StandardCharsets.UTF_8).trim()
            throw new GradleException(error ?: "${mainClassName} failed with exit code ${result.exitValue}")
        }
        out.toString(StandardCharsets.UTF_8).readLines().findAll { !it.isBlank() }*.trim()
    }
}
