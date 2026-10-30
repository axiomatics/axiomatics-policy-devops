package upgrade

import com.axiomatics.apd.authhub.integration.attributeconns.LegacyConnectorFinder
import com.axiomatics.apd.authhub.integration.attributeconns.MigratableConnectorScanner

/**
 * Upgrades legacy attribute connectors (className + XML configurationString) to the new YAML configuration format.
 * <p>
 * Finding upgradable connectors only reads files. The conversion itself uses the attribute connector library, which
 * is not on the Gradle build classpath, so it runs in a separate JVM via {@link UpgradeRunner}.
 */
class AttributeConnectorUpgradeStep implements UpgradeStep {

    // Authorization domain folder layout (domtool convention)
    static final String CONNECTORS_DIR = "attributeConnectors"

    /** Entry point in axio-attribute-connectors-apd-platform, part of the APD bundle */
    static final String UPGRADE_MAIN_CLASS = "com.axiomatics.apd.connectors.upgrade.UpgradeAttributeConnectorsMain"

    private final File authzDomainDir
    private final File connectorsDir

    /** @param authzDomainDir the authorization domain root folder, e.g. src/authorizationDomain */
    AttributeConnectorUpgradeStep(File authzDomainDir) {
        this.authzDomainDir = authzDomainDir
        this.connectorsDir = new File(authzDomainDir, CONNECTORS_DIR)
    }

    @Override
    String getDescription() {
        "The following Axiomatics attribute connectors can be upgraded to the new YAML configuration format:"
    }

    @Override
    String getUpgradedDescription() {
        "Upgraded the following Axiomatics attribute connectors to the new YAML configuration format:"
    }

    @Override
    List<String> getPendingItems() {
        // Runs at the end of every build: the fast scan avoids parsing connectors in the normal case where there is
        // nothing to upgrade
        if (!MigratableConnectorScanner.mayContainMigratableConnectors(connectorsDir.toPath())) {
            return []
        }
        try {
            return LegacyConnectorFinder.find(connectorsDir.toPath()).keySet() as List<String>
        } catch (Exception ignored) {
            // a broken connector: fail fast and silently, it is reported by the tasks that use the connectors
            return []
        }
    }

    @Override
    List<String> apply(UpgradeRunner runner) {
        // no need to start a JVM when the fast scan rules out any upgradable connector
        if (!MigratableConnectorScanner.mayContainMigratableConnectors(connectorsDir.toPath())) {
            return []
        }
        // not silent: an explicitly requested upgrade must report broken connectors
        runner.run(UPGRADE_MAIN_CLASS, [authzDomainDir.absolutePath])
    }
}
