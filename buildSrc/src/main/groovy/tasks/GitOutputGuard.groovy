package tasks

import org.ajoberstar.grgit.Grgit
import org.gradle.api.GradleException
import org.gradle.api.Task

class GitOutputGuard {

    static void check(Task task, File outputPath) {
        def grgit = null
        try {
            grgit = Grgit.open(currentDir: task.project.rootDir)
            def rel = task.project.rootDir.toPath()
                    .relativize(outputPath.toPath())
                    .toString()
                    .replace('\\', '/')
            def status = grgit.status()
            def allChanged = ([] + status.staged.modified + status.staged.added + status.staged.removed
                                 + status.unstaged.modified + status.unstaged.removed) as Set

            // Check both the path itself (file case) and any path under it (directory case).
            // This also catches deletions of tracked files/dirs that no longer exist on disk.
            boolean hasChanges = allChanged.contains(rel) || allChanged.any { it.startsWith(rel + '/') }

            if (hasChanges) {
                if (System.getProperty('apd.allowUnsafe') == 'true' ||
                        task.project.findProperty('apd.allowUnsafe') == 'true') {
                    task.logger.warn(
                            "WARNING: Task ${task.name}: ${rel} has uncommitted changes but " +
                            "apd.allowUnsafe=true - proceeding anyway.")
                    return
                }
                throw new GradleException(
                        "Task ${task.name} aborted: ${rel} has uncommitted changes. " +
                        "Commit or stash your changes first, " +
                        "or bypass this check with: gradlew ${task.name} -Dapd.allowUnsafe=true")
            }
        } catch (GradleException e) {
            throw e
        } catch (Exception ignored) {
            // not a git repo or git unavailable — allow
        } finally {
            grgit?.close()
        }
    }
}
