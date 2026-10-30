package com.axiomatics.domtool.cli

import org.gradle.api.file.FileTree

import java.nio.file.Path

public class ConfigurableDomainCommand extends com.axiomatics.domtool.cli.DomainCommand {

    // NOTE! Do not move this class to another pacakge, it needs to access package private methods

    /** Prefix the ALFA compiler gives policy and policy set ids: {@code acme.Main} is {@code <prefix>acme.Main}. */
    public static final String MAIN_POLICY_PREFIX = "http://axiomatics.com/alfa/identifier/"
    public File srcDir;
    public FileTree xmlPolicies;
    public String mainPolicy;
    public File outputYamlFile
    public String domainIdentityGitHash;
    public Path metadataYamlFile

    public Integer call() {

        super.inputPath =   srcDir.toPath()
        super.mainPolicyIdentifierOverride =  MAIN_POLICY_PREFIX + mainPolicy
        super.xacmlSpecificationsPath = xmlPolicies.getDir().toPath()
        super.outputPath = outputYamlFile.toPath()
        super.domainIdentity = domainIdentityGitHash
        super.metadataPath = metadataYamlFile
        super.call();
    }
}
