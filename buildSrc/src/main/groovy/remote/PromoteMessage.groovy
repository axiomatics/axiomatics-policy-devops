package remote

import org.ajoberstar.grgit.Grgit

/**
 * The message a push task sends with its AuthHub draft promotes (stored on the promote's change set):
 * {@code <task name>: <commit>}, the commit as {@code git describe --always --dirty --exclude='*'} prints it
 * (abbreviated commit id, {@code -dirty} when tracked files have uncommitted changes; untracked files do not count).
 * Outside a git repository only the task name.
 */
class PromoteMessage {

    static String of(String taskName, File projectDir) {
        String commit = describe(projectDir)
        commit ? "${taskName}: ${commit}".toString() : taskName
    }

    /** The commit of {@code dir}'s git repository as {@code git describe --always --dirty --exclude='*'}, or null. */
    static String describe(File dir) {
        def grgit = null
        try {
            grgit = Grgit.open(currentDir: dir)
            def status = grgit.status()
            boolean dirty = !(status.staged.allChanges.empty && status.unstaged.modified.empty && status.unstaged.removed.empty)
            return grgit.head().abbreviatedId + (dirty ? '-dirty' : '')
        } catch (Exception ignored) {
            // not a git repo, or a repository without commits
            return null
        } finally {
            grgit?.close()
        }
    }
}
