package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** An old task name that runs its replacement (dependsOn) and warns that the name is deprecated. */
class DeprecatedTaskAlias extends DefaultTask {

    @Internal
    String replacement

    DeprecatedTaskAlias() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void warn() {
        logger.warn("Task '${name}' is deprecated and will be removed, use '${replacement}' instead.")
    }
}
