package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import upgrade.AuthzDomainUpgrades
import upgrade.UpgradeRunner
import upgrade.UpgradeStep

import javax.inject.Inject

/**
 * Upgrades authorization domain content to newer formats. There is no rollback: the task refuses to run unless
 * all files are committed, so the user can review the changes with git and undo them if needed.
 */
abstract class UpgradeAuthzDomainTask extends DefaultTask {

    /** The authorization domain root folder, e.g. src/authorizationDomain */
    @Internal
    abstract DirectoryProperty getAuthzDomainDir()

    /** Classpath for upgrade code run in a separate JVM: the project's test runtime classpath (APD bundle) */
    @Classpath
    abstract ConfigurableFileCollection getUpgradeClasspath()

    @Inject
    abstract ExecOperations getExecOperations()

    @Internal
    File projectDir

    @Internal
    boolean allowUnsafe

    @TaskAction
    void upgrade() {
        GitOutputGuard.checkWorkingTreeClean(this, projectDir, allowUnsafe)

        UpgradeRunner runner = new UpgradeRunner(execOperations, upgradeClasspath)
        List<String> summaries = []
        for (UpgradeStep step : AuthzDomainUpgrades.all(authzDomainDir.get().asFile)) {
            List<String> upgraded = step.apply(runner)
            if (!upgraded.isEmpty()) {
                summaries << AuthzDomainUpgrades.upgradedSummary(step, upgraded)
            }
        }

        if (summaries.isEmpty()) {
            return
        }
        logger.lifecycle(AuthzDomainUpgrades.framed(summaries + AuthzDomainUpgrades.NEXT_STEPS))
    }
}
