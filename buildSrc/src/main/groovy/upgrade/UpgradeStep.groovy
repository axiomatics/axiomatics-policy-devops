package upgrade

/**
 * One kind of authorization domain upgrade, e.g. legacy attribute connectors to the new YAML configuration format.
 * Register new steps in {@link AuthzDomainUpgrades#all}.
 */
interface UpgradeStep {

    /** Headline shown above the pending items, e.g. "The following Axiomatics attribute connectors can be upgraded ...". */
    String getDescription()

    /** Headline shown above the upgraded items, e.g. "Upgraded the following Axiomatics attribute connectors ...". */
    String getUpgradedDescription()

    /**
     * Items that can be upgraded. Empty when there is nothing to do, or when the content cannot be read: this runs
     * at the end of every build, so it must fail fast and silently. Must not change any files.
     */
    List<String> getPendingItems()

    /**
     * Performs the upgrade and returns the upgraded items.
     * @param runner runs upgrade code that needs more than the Gradle build classpath, in a separate JVM
     */
    List<String> apply(UpgradeRunner runner)
}
