package upgrade

/**
 * Registry of all authorization domain upgrade steps, and the texts shown to the user.
 */
class AuthzDomainUpgrades {

    static final String UPGRADE_TASK = "upgradeAuthzDomain"
    static final String CHECK_TASK = "checkAuthzDomainUpgrade"

    static final String HOW_TO_UPGRADE =
            "To run the upgrade, commit all files and run the Gradle task ${UPGRADE_TASK}, for example: ./gradlew ${UPGRADE_TASK}"

    static final String NEXT_STEPS = """Upgrade done. Next steps:
  1. Verify that everything works as before: run ./gradlew runAds, and/or run your tests with ./gradlew test.
  2. Inspect the changed files manually, for example with git diff.
  3. Commit the changes."""

    static final String NOTHING_TO_UPGRADE = "The authorization domain is up to date, nothing to upgrade."

    static final String FRAME = '-' * 70

    private AuthzDomainUpgrades() {}

    /**
     * All upgrade steps, in the order they are applied. Add new steps here.
     * @param authzDomainDir the authorization domain root folder, e.g. src/authorizationDomain. Each step works out
     *                       the files it needs from the domtool folder layout convention.
     */
    static List<UpgradeStep> all(File authzDomainDir) {
        [
                new AttributeConnectorUpgradeStep(authzDomainDir),
        ]
    }

    /**
     * Notice listing everything that can be upgraded, framed to stand out in the build output.
     * Only reads files. Returns null when there is nothing to upgrade.
     */
    static String pendingNotice(File authzDomainDir) {
        List<String> summaries = all(authzDomainDir).findResults { UpgradeStep step ->
            List<String> pending = step.pendingItems
            pending.isEmpty() ? null : summary(step, pending)
        }
        summaries.isEmpty() ? null : framed(summaries + HOW_TO_UPGRADE)
    }

    /** Surrounds the lines with blank lines and a frame, so they stand out in the build output. */
    static String framed(List<String> lines) {
        (['', FRAME] + lines + [FRAME, '']).join('\n')
    }

    static String summary(UpgradeStep step, List<String> items) {
        list(step.description, items)
    }

    static String upgradedSummary(UpgradeStep step, List<String> items) {
        list(step.upgradedDescription, items)
    }

    private static String list(String headline, List<String> items) {
        ([headline] + items.collect { "  - ${it}" }).join('\n')
    }
}
