package tasks

import org.ajoberstar.grgit.Grgit
import org.gradle.api.GradleException
import org.gradle.api.Task

class GitOutputGuard {

    static void checkAll(Task callerTask, File rootDir, boolean allowUnsafeRequested, Map<String, File> outputPathsByTaskName) {
        def grgit = null
        try {
            grgit = Grgit.open(currentDir: rootDir)
            def status = grgit.status()
            def allChanged = ([] + status.staged.modified + status.staged.added + status.staged.removed
                                 + status.unstaged.modified + status.unstaged.removed) as Set

            List<String> dirty = []
            outputPathsByTaskName.each { taskName, outputPath ->
                def rel = rootDir.toPath()
                        .relativize(outputPath.toPath())
                        .toString()
                        .replace('\\', '/')
                if (allChanged.contains(rel) || allChanged.any { it.startsWith(rel + '/') })
                    dirty << rel
            }
            if (!dirty.empty) {
                boolean allowUnsafe = System.getProperty('apd.allowUnsafe') == 'true' || allowUnsafeRequested
                if (allowUnsafe) {
                    dirty.each {
                        callerTask.logger.warn(
                                "WARNING: Task ${callerTask.name}: ${it} has uncommitted changes but " +
                                "apd.allowUnsafe=true - proceeding anyway.")
                    }
                } else {
                    throw new GradleException(
                            "Task ${callerTask.name} aborted: the following paths have uncommitted changes:\n" +
                            dirty.collect { "  - ${it}" }.join('\n') +
                            "\nCommit or stash your changes first, " +
                            "or bypass this check with: gradlew <task> -Dapd.allowUnsafe=true")
                }
            }
        } catch (GradleException e) {
            throw e
        } catch (Exception ignored) {
            // not a git repo or git unavailable — allow
        } finally {
            grgit?.close()
        }
    }

    /**
     * Requires the whole working tree to be clean, including untracked files. Unlike {@link #check}, a missing
     * git repository is also refused: git is then the only way to undo the task's changes.
     */
    static void checkWorkingTreeClean(Task task, File rootDir, boolean allowUnsafeRequested) {
        boolean allowUnsafe = System.getProperty('apd.allowUnsafe') == 'true' || allowUnsafeRequested
        String problem
        def grgit = null
        try {
            grgit = Grgit.open(currentDir: rootDir)
            if (grgit.status().clean) {
                return
            }
            problem = "there are uncommitted changes or untracked files"
        } catch (Exception e) {
            problem = "${rootDir} is not a git repository (${e.message})"
        } finally {
            grgit?.close()
        }

        if (allowUnsafe) {
            task.logger.warn("WARNING: Task ${task.name}: ${problem}\napd.allowUnsafe=true - proceeding anyway.")
            return
        }
        throw new GradleException(
                "Task ${task.name} aborted: ${problem}\n" +
                "Commit all files first, so the changes made by ${task.name} can be reviewed and undone with git, " +
                "or bypass this check with: gradlew ${task.name} -Dapd.allowUnsafe=true")
    }

    static void check(Task task, File rootDir, boolean allowUnsafeRequested, File outputPath) {
        def grgit = null
        try {
            grgit = Grgit.open(currentDir: rootDir)
            def rel = rootDir.toPath()
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
                if (System.getProperty('apd.allowUnsafe') == 'true' || allowUnsafeRequested) {
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
