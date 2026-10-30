package extensions

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import remote.RemoteTasks
import tasks.GitInfoValueSource

abstract class AlfaExtension {

    /** APD version that removes the root {@code project}/{@code namespace} (APD-208). */
    static final String ROOT_PROJECT_REMOVED_IN = "27.1"

    /**
     * Default AuthHub project (ASM7: namespace) of the remotes that set none.
     * @deprecated since 26.2, removed in 27.1 (APD-208): set {@code project} in each remote,
     *             {@code repositories { hub { project 'x' } }}
     */
    @Deprecated(since = "26.2", forRemoval = true)
    String namespace
    // whether namespace was set in build.gradle, not only defaulted to the root project name
    private boolean namespaceSet
    String mainpolicy = null
    String deploymentDescriptor = "deployment.yaml"
    String srcDir = "src/authorizationDomain"
    String licenseFile = "license/axiomatics_ADS.license"
    String dockerName
    boolean extraAttributeIdCheck = true
    Project project
    Map<String,String> metadata = new HashMap<>()
    Boolean withVisualTrace = true

    // Identity string for the generated authorization domain. Defaults to a git-derived
    // "repo-commit" value (see GitInfoValueSource) but stays overridable — an explicit
    // `alfa { domainIdentity = 'x' }` always wins over the convention.
    abstract Property<String> getDomainIdentity()

    DomainRepositories repositories = new DomainRepositories();

    private Provider<Map<String, String>> gitInfo

    AlfaExtension(Project project) {
        this.project = project;
        namespace = this.project.getRootProject().getName().replaceAll("-", "_").replaceAll("\\.", "_")
        gitInfo = project.providers.of(GitInfoValueSource) {
            it.parameters.rootDir.set(project.layout.projectDirectory)
        }
        domainIdentity.convention(gitInfo.map { it.repoAndCommit })
    }

    void repositories(@DelegatesTo(value = DomainRepositories, strategy = Closure.DELEGATE_FIRST) Closure c) {
        int registered = repositories.adms.size()
        project.configure(repositories, c);
        // tasks of the remotes added by this closure; promote tasks from every remote declared before
        for (int i = registered; i < repositories.adms.size(); i++) {
            nameRemote(repositories.adms[i], repositories.adms.subList(0, i))
            warnIfRootProjectUsed(repositories.adms[i])
            RemoteTasks.register(project, this, repositories.adms[i], repositories.adms.subList(0, i))
        }
    }

    // domainName is required; environment names the tasks of a remote, without it <project>_<domainName>, which must be unique
    private void nameRemote(AdmExtension remote, List<AdmExtension> earlierRemotes) {
        if (!remote.domainName) {
            throw new GradleException("APD: set domainName on every remote in alfa { repositories } (host ${remote.host}).")
        }
        if (!remote.environment) {
            remote.environment = "${projectOf(remote)}_${remote.domainName}".replaceAll('[^A-Za-z0-9_]', '_')
        }
        AdmExtension sameName = earlierRemotes.find { it.environment.equalsIgnoreCase(remote.environment) }
        if (sameName) {
            throw new GradleException("APD: two remotes in alfa { repositories } get the same task names " +
                    "('${remote.environment}'): ${sameName.host} and ${remote.host}. Set a different environment " +
                    "on one of them, for example environment 'prod'.")
        }
    }

    /** @deprecated since 26.2, removed in 27.1 (APD-208): set {@code project} in each remote */
    @Deprecated(since = "26.2", forRemoval = true)
    void namespace(String spec) {
        setNamespace(spec)
    }

    /** @deprecated since 26.2, removed in 27.1 (APD-208): set {@code project} in each remote */
    @Deprecated(since = "26.2", forRemoval = true)
    void project(String spec) {
        setNamespace(spec)
    }

    /** {@code alfa { namespace = 'x' }}; @deprecated since 26.2, removed in 27.1 (APD-208) */
    @Deprecated(since = "26.2", forRemoval = true)
    void setNamespace(String spec) {
        this.namespace = spec
        if (!namespaceSet) {
            project.logger.warn("APD: alfa { project '${spec}' } (or namespace) is deprecated and will be removed in APD " +
                    "${ROOT_PROJECT_REMOVED_IN}. Set the project in each remote instead: hub { project '${spec}' … } " +
                    "or remove it completly if you have no AuthHub or ASM integrations.")
        }
        namespaceSet = true
    }

    /**
     * The AuthHub project (ASM7: namespace) of a remote: its own {@code project}, otherwise the deprecated root
     * {@code project}/{@code namespace}, otherwise the root project name.
     */
    String projectOf(AdmExtension remote) {
        remote.projectName ?: namespace
    }

    // at registration, so the root project may still be set later in alfa {}: the message names no value
    private void warnIfRootProjectUsed(AdmExtension remote) {
        if (remote.projectName) {
            return
        }
        project.logger.warn("APD: remote '${remote.environment}' has no project, so it uses alfa { project } or, if that " +
                "is not set, the root project name. Both are deprecated and will be removed in APD " +
                "${ROOT_PROJECT_REMOVED_IN}. Add project '<AuthHub project>' to the remote.")
    }

    @Override
    public String toString() {
        return "AlfaExtension{" +
                "namespace='" + namespace + '\'' +
                ", mainpolicy='" + mainpolicy + '\'' +
                ", deploymentDescriptor='" + deploymentDescriptor + '\'' +
                ", srcDir='" + srcDir + '\'' +
                ", licenseFile='" + licenseFile + '\'' +
                ", dockerName='" + dockerName + '\'' +
                ", project=" + project +
                ", repositories=" + repositories +
                ", withVisualTrace=" + withVisualTrace +
                ", metadata =" + metadata +
                '}';
    }


    static final String GENERATE_ALFA_DICTIONARY_TASK = "generateAlfaDictionaryFromYaml"
    static final String GENERATE_JAVA_DICTIONARY_TASK = "generateJavaDictionaryFromYaml"

    Provider<RegularFile> getAttributesYamlFile() {
        project.layout.projectDirectory.file(project.provider { "${srcDir}/attributes.yaml" })
    }

    Provider<RegularFile> getAttributesAlfaFile() {
        project.layout.projectDirectory.file(project.provider { "${srcDir}/alfaSpecifications/attributes.alfa" })
    }

    Provider<RegularFile> getDictionaryJavaFile() {
        project.layout.projectDirectory.file(project.provider {
            File testJavaDir = project.sourceSets.test.java.srcDirs.first()
            new File(testJavaDir, 'apd/Dictionary.java').path
        })
    }

    Provider<String> getGitCommitAuthor() {
        gitInfo.map { it.author }
    }

    Provider<String> getGitCommitMessage() {
        gitInfo.map { it.message }
    }

    Provider<String> getCommitRef() {
        gitInfo.map { it.commitRef }
    }

    Provider<String> getCommitDate() {
        gitInfo.map { it.commitDate }
    }

    public class DomainRepositories {
        List<AdmExtension> adms = []

        void adm(@DelegatesTo(value = AdmExtension, strategy = Closure.DELEGATE_FIRST) Closure c) {
            AdmExtension adm = new AdmExtension(project, AlfaExtension.this)
            project.configure(adm, c);
            adm.repoType("asm")
            adms.add(adm);
        }
        void hub(@DelegatesTo(value = AdmExtension, strategy = Closure.DELEGATE_FIRST) Closure c) {
            AdmExtension adm = new AdmExtension(project, AlfaExtension.this)
            project.configure(adm, c);
            adms.add(adm);
        }
        void remote(@DelegatesTo(value = AdmExtension, strategy = Closure.DELEGATE_FIRST) Closure c) {
            AdmExtension adm = new AdmExtension(project, AlfaExtension.this)
            project.configure(adm, c);
            adms.add(adm);
        }


        @Override
        public String toString() {
            return "DomainRepositories{" +
                    "adms=" + adms +
                    '}';
        }
    }
}