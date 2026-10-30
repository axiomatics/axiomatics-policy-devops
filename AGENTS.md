# AGENTS.md

Instructions for AI coding agents working in this Axiomatics Policy DevOps (APD) project. Humans: see `README.md` and
the documentation at https://docs.axiomatics.com/axiomatics-policy-devops/.

This file belongs to APD and is replaced when APD is updated. Put your own instructions for agents in
`AGENTS.local.md` in the project root.

**Agents: if `AGENTS.local.md` exists, read it too. Its instructions take precedence over this file.**

## What this project is

An APD project contains attribute-based access control (ABAC) policies written in ALFA, the attribute dictionary, the
attribute connectors and JUnit tests of the policies. Gradle compiles them into an authorization domain, which the
Axiomatics Access Decision Service (ADS) evaluates. The domain can be pushed to and pulled from Axiomatics
Authorization Hub, and the project builds a container image of ADS with the domain.

- APD documentation: https://docs.axiomatics.com/axiomatics-policy-devops/
- ALFA introduction: https://axiomatics.github.io/alfa-vscode-doc/docs/alfa-introduction/introduction/

## Project layout

| Path | Owner | Notes |
|---|---|---|
| `src/authorizationDomain/alfaSpecifications/` | user | ALFA policies; `attributes.alfa` is the attribute dictionary in ALFA |
| `src/authorizationDomain/attributes.yaml` | user | The attribute dictionary in YAML |
| `src/authorizationDomain/attributeConnectors/` | user | Attribute connectors (PIPs), one YAML file each |
| `src/authorizationDomain/*.yaml` | user | Other domain settings: `metadata.yaml`, `decisionParameters.yaml`, `attributeCache.yaml` |
| `src/test/` | user | JUnit 5 tests of the policies and attribute connectors |
| `build.gradle` | user | Project settings: `alfa { mainpolicy …, repositories { hub { … } } }`, dependencies, test settings |
| `settings.gradle` | user (`rootProject.name` only) | The rest belongs to APD |
| `Dockerfile`, `deployment.yaml`, `src/extra/` | user | The ADS image: `src/extra` (`startService.sh`, `logback-custom.xml`, keystores) is copied into the image |
| `license/` | user | ADS license (`axiomatics_ADS.license`). Do not commit unless the user says so |
| `gradle.properties` | user | Gradle settings; never put secrets here |
| `AGENTS.local.md` | user | Project-specific instructions for agents |
| `README.md`, `CODEOWNERS` | user | The project's description and code owners; APD ships its own, which the user replaces |
| `buildSrc/`, `gradle/`, `gradlew`, `gradlew.bat` | APD | Do not edit; replaced on update. Put customizations in `build.gradle` |
| `AGENTS.md`, `extra/` | APD | Replaced on update |
| `build/` | generated | Build output, not committed |

The attribute dictionary exists in two forms that must stay in sync. Edit one, then regenerate the other:
`generateAlfaDictionaryFromYaml` (YAML → `attributes.alfa`) or `generateYamlDictionaryFromAlfa` (ALFA →
`attributes.yaml`). `generateJavaDictionaryFromYaml` generates Java constants of the attributes for the tests. These tasks
refuse to overwrite files with uncommitted changes.

## Commands

Use the Gradle wrapper (`./gradlew` on Linux/macOS, `gradlew.bat` on Windows). The first build downloads dependencies
from the Axiomatics repository, which needs `AXIOMATICS_ACCESS_KEY_ID` and `AXIOMATICS_SECRET_ACCESS_KEY` (see
Secrets).

| Command | What it does |
|---|---|
| `./gradlew test` | Compiles the ALFA and runs all tests. **Run it after every change** to policies, attributes, connectors or tests |
| `./gradlew test --tests "com.myorg.alfa.MySystemTest"` | Runs one test class |
| `./gradlew buildAuthzDomain` | Builds the authorization domain into `build/alfa/domain/ads/domain.yaml` |
| `./gradlew runAds` | Starts ADS locally with the domain (runs until stopped) |
| `./gradlew stageDeployment` | Stages the image build context in `build/install/deployment/ads/` |
| `./gradlew tasks` | Lists all tasks, including the tasks of each remote in `build.gradle` |
| `./gradlew printHubConnections` | Prints, for each remote, how an ADS loads its domain and the metadata of that domain. Only reads; safe |

Test reports are in `build/reports/tests/test/index.html`.

## Writing tests

Tests use the `AlfaExtension` JUnit 5 extension. Follow the existing tests in `src/test/java`:

```java
@RegisterExtension
public AlfaExtension alfa = new AlfaExtension().withMainPolicy("consultants.Consultants");

@Test
@DisplayName("REQ-ABAC-1: Should permit a Consultant to access resource in same location")
public void shouldPermitIfUserAndResourceAreInSameLocation() {
    TestResponse result = alfa.newTestRequest()
            .with("user.role", "consultant")
            .with("user.location", "hawaii")
            .with("resource.location", "hawaii")
            .evaluate();
    assertThat(result, is(permit()));
}
```

- **Unit tests** set all attributes in the request and test one policy (`withMainPolicy`).
- **System tests** use `new AlfaExtension().withAttributeConnectors()` and fetch attributes through the attribute
  connectors, so they need the systems behind the connectors.
- Add or change tests together with every policy change, covering both permit and deny.
- Give each test a `@DisplayName` with the requirement it verifies, for example `REQ-ABAC-1: Should permit …`; use the
  user's requirement ids when they have them.

## Secrets

Never write secrets into files of this project, never commit them, and never print them in the output. Secrets are:

- the Authorization Hub (or ADM/ASM) credentials: `client_secret` and `password` in the remotes of `build.gradle`;
- `AXIOMATICS_ACCESS_KEY_ID` and `AXIOMATICS_SECRET_ACCESS_KEY`;
- keystore and truststore passwords, and the private keys in keystores;
- the ADS password (`ADS_PASSWORD`) and the license file.

In `build.gradle`, read secrets from environment variables:

```groovy
hub {
    host 'https://myhub.acme.com'
    project 'MyProject'
    domainName 'DevStaging'
    oidcCredentials {
        client_id 'apd'
        client_secret System.getenv('HUB_CLIENT_SECRET')
    }
}
```

The user sets the environment variable in their shell or CI pipeline. The Axiomatics access keys go in environment
variables or in `$HOME/.gradle/gradle.properties`, outside the project. If you find a secret in a tracked file, tell the
user; do not commit it. Before every commit, check the diff for secrets.

## Ask the user before

- tasks that change a remote system: `…PushTo<env>`, `DomainPromoteFrom<env>To<env>`, and `…PullFrom<env>`, which
  overwrites local files;
- `upgradeAuthzDomain`: it rewrites legacy attribute connectors (XML → YAML); `checkAuthzDomainUpgrade` only reports and
  is safe;
- `-Dapd.allowUnsafe=true` or `-Papd.allowUnsafe=true`: it disables the check that protects uncommitted changes;
- commit, push, or changing git history.

## Updating APD

The steps and the conflict table are on the Update page of the documentation:
https://docs.axiomatics.com/axiomatics-policy-devops/getting-started/update. Read it, and the release notes
(https://docs.axiomatics.com/axiomatics-policy-devops/release-notes) of every version between the installed and the
new one, before you start. Then:

1. **Check the current version:** `./gradlew -q dependencies --configuration testCompileClasspath`, and look for
   `com.axiomatics`.
2. **Prepare:** the git working tree must be clean. If it is not, stop and ask the user to commit or stash. Run
   `./gradlew test` and note the result, so you can compare after the update. If `buildSrc/` has local changes
   compared with the installed APD version, stop: the user must move them to `build.gradle` first.
3. **Work on a new branch**, for example `apd-update-<version>`.
4. **Get the new version.** APD is published at https://github.com/axiomatics/axiomatics-policy-devops (branch
   `main`).
   - **Git way**, when the project shares history with that repository (`git merge-base` finds a common commit): add
     it as a remote if needed (`git remote add apd https://github.com/axiomatics/axiomatics-policy-devops.git`),
     then `git fetch apd` and `git merge apd/main`.
   - **Manual way** otherwise (do not merge unrelated histories): clone it into a temporary directory outside the
     project. Replace the files that belong to APD (step 5) with the new ones, and compare every other file of the
     new version with the project's: treat each difference like a merge conflict in step 5.
5. **Resolve conflicts** with the conflict table of the Update page:
   - `buildSrc/`, `gradle/`, `gradlew`, `gradlew.bat`, `AGENTS.md`, `extra/`: take APD's version.
   - `settings.gradle`: take APD's version, keep the user's `rootProject.name`.
   - `build.gradle`, `deployment.yaml`, `Dockerfile`, `src/extra/`: keep the user's version, then compare it with APD's
     new version and list the differences for the user. Adopt a change only when the release notes or the Update page
     require it, or the user agrees.
   - `src/authorizationDomain/`, `src/test/`, `license/`, `AGENTS.local.md`, `README.md`, `CODEOWNERS`,
     `gradle.properties`: keep the user's version. Never delete or
     overwrite the user's policies, attributes, connectors or tests with APD's sample files. APD's sample files that
     are new in the project can be dropped if the user had removed the samples.
   - Anything else: ask the user.
6. **Apply the version-specific steps** of the Update page and the release notes, for example deprecations. Propose
   optional changes (such as switching to the current ADS image) to the user instead of making them silently.
7. **Verify:** check the version again, run `./gradlew test` and `./gradlew buildAuthzDomain`, and compare the test
   result with the one before the update. If `checkAuthzDomainUpgrade` reports content to upgrade, tell the user.
8. **Report** to the user: the old and new version, the conflicts and how you resolved each, the differences you did
   not adopt, the steps that need the user, and the test result. Commit only when the user agrees.

## Deprecated, do not use for new work

- ADS 1: the tasks `runAdsV1` and `buildAdsDockerImage` and `extra/exampleAds1Configuration`. Removed in APD 27.1. Use
  the current ADS: `runAds`, `stageDeployment` and the project's `Dockerfile`.
- Out-of-the-box support for ASM 7, ASM 6 and standalone ADM (`adm { }` remotes). Removed in APD 27.1. Use
  Authorization Hub (`hub { }`).
- `alfa { project 'x' }` and `alfa { namespace 'x' }`: set `project 'x'` in each remote instead.
