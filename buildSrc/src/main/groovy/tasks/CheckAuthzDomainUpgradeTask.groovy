package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import upgrade.AuthzDomainUpgrades

/**
 * Reports authorization domain content that can be upgraded to a newer format, on demand. The same report is
 * shown automatically at the end of every successful build by {@link upgrade.AuthzDomainUpgradeNotice}.
 * Only reads files, and never fails the build. Has no outputs on purpose, so it is never UP-TO-DATE.
 */
abstract class CheckAuthzDomainUpgradeTask extends DefaultTask {

    /** The authorization domain root folder, e.g. src/authorizationDomain */
    @Internal
    abstract DirectoryProperty getAuthzDomainDir()

    @TaskAction
    void check() {
        String notice
        try {
            notice = AuthzDomainUpgrades.pendingNotice(authzDomainDir.get().asFile)
        } catch (Exception e) {
            // silent: broken content is reported by the tasks that use it
            logger.info("Could not check if the authorization domain can be upgraded", e)
            return
        }
        if (notice == null) {
            logger.info(AuthzDomainUpgrades.NOTHING_TO_UPGRADE)
            return
        }
        logger.warn(notice)
    }
}
