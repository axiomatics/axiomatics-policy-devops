package upgrade

import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Gradle dataflow action that runs when the build ends, whatever tasks were invoked, and prints a notice if the
 * authorization domain can be upgraded. Prints nothing on failed builds, and never fails the build itself.
 * Registered in AlfaPlugin.
 */
abstract class AuthzDomainUpgradeNotice implements FlowAction<Parameters> {

    private static final Logger LOG = Logging.getLogger(AuthzDomainUpgradeNotice)

    interface Parameters extends FlowParameters {
        @Input
        Property<Boolean> getBuildFailed()

        /** Absolute path of the authorization domain root folder, e.g. src/authorizationDomain */
        @Input
        Property<String> getAuthzDomainDir()
    }

    @Override
    void execute(Parameters parameters) {
        if (parameters.buildFailed.get()) {
            return
        }
        try {
            String notice = AuthzDomainUpgrades.pendingNotice(new File(parameters.authzDomainDir.get()))
            if (notice != null) {
                LOG.warn(notice)
            }
        } catch (Exception e) {
            LOG.info("Could not check if the authorization domain can be upgraded", e)
        }
    }
}
