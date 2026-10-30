package tasks.authhub

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import tasks.GitOutputGuard

/**
 * Git guard run before the pulls of AllPullFrom...: checks that all their output paths are clean before any of them
 * writes. Never up-to-date.
 */
class PullPreCheckTask extends DefaultTask {

    /** Output path per pull task name. */
    @Internal
    Map<String, File> guardedPaths = [:]

    @Internal
    File rootDir

    @Internal
    boolean allowUnsafe

    PullPreCheckTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void check() {
        GitOutputGuard.checkAll(this, rootDir, allowUnsafe, guardedPaths)
    }
}
