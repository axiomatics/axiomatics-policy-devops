## Axiomatics Policy Devops

This repository is the entry point to start with Attribute Base Access Control (ABAC) and Policy-as-code from Axiomatics.

## Documentation

Online documentation from Axiomatics is available at https://docs.axiomatics.com/axiomatics-policy-devops/
Axiomatics Labs with developer resources is available at https://axiomatics.com/labs/


ALFA introduction available at https://axiomatics.github.io/alfa-vscode-doc/docs/alfa-introduction/introduction/

### Quick start

1. Add the Axiomatics access key ID and secret access key, that you received from Axiomatics, in file
   `~/.gradle/gradle.properties` or as environment variables (`AXIOMATICS_ACCESS_KEY_ID`, `AXIOMATICS_SECRET_ACCESS_KEY`).
   Do not commit them.
2. Make sure your IDE, such as IntelliJ or Visual Studio Code has the necessary plugins for Java and Gradle projects
3. Open this project in your IDE, it contains a sample project
4. Execute gradle task `test` to test your policies and attribute connectors
5. (Optional) Execute gradle task `buildAuthzDomain` to build the authorization domain into file `build/alfa/domain/ads/domain.yaml`
6. Copy the Axiomatics ADS license, that you received from Axiomatics, to `license/axiomatics_ADS.license`
7. (Optional) Execute gradle task `runAds` to start the Access Decision Service locally
8. Execute gradle task `stageDeployment` to stage the configuration and binaries for the container image build
   context in `build/install/deployment/ads/`
9. Build the container image with the tool available in your pipeline environment, for example:
   ```
   docker build -t <registry>/<image>:<tag> build/install/deployment/ads/
   ```
10. Push the image to your registry, for example:
    ```
    docker push <registry>/<image>:<tag>
    ```

The project is set up for Access Decision Service (ADS) 26: `Dockerfile` and `deployment.yaml` in the project root,
`startService.sh` and `logback-custom.xml` in `src/extra`. The ADS settings in `deployment.yaml` can be set with
environment variables (`ADS_LICENSE`, `ADS_PORT`, `ADS_USERNAME`, `ADS_PASSWORD`, ...); `stageDeployment` lists them
in `build/install/deployment/.env.generated`.

The configuration for the legacy ADS 1 is in `extra/exampleAds1Configuration`. ADS 1 support is deprecated and will be
removed in APD 27.1.

AI coding agents find their instructions in `AGENTS.md`, including how to update APD. Put your own instructions for
agents in `AGENTS.local.md`.

### ALFA
A simple policy example allowing all consultants to access resources in their own location.

#### main.alfa
```groovy
namespace acme {

    policyset Main {
        apply firstApplicable
        managers.Managers
        consultants.Consultants
    }
}
```

#### consultants/main.alfa
```groovy
namespace consultants {
    policy Consultants {
        target
            clause user.role == "consultant"

        apply firstApplicable
        rule permitIfLocationsMatch {
            permit
            condition user.location == resource.location
        }
    }
}
```
### Test

A simple example test that verifies Cecilia can access resource 1. This test is a system tests so it assumes test PIP data that Cecilia is a consultant and located in the same location as resource 1.

```java
public class MySystemTest {

    @RegisterExtension
    public AlfaExtension alfa = new AlfaExtension().withAttributeConnectors();

    @Test
    @DisplayName("REQ-ABAC-1: Should permit consultant Cecilia to access resource 1 in her location")
    public void shouldGiveCeciliaAccessToResource1() {
        TestRequest target = alfa.newTestRequest()
                .with("user.identity", "cecilia")
                .with("resource.identity", "1");

        TestResponse result = target.evaluate();

        assertThat(result, is(permit()));
    }
}
```

#### Test trace visualisation

After test run, a visualization trace can be produced. It shows how the policy was evaluated and which attributes were fetched from PIPs. This gives an overview of the policy or when detail mode is enabled, evaluation result of any function is shown to support debugging. Note that test trace visualisation is currently released in as a preview feature and is not covered by SLA.

##### High level policy evaluation view
![shouldGiveCeciliaAccessToResource1](https://github.com/user-attachments/assets/82da82b9-7c82-45ff-89d6-c01ad77c4b25)

##### Detailed level policy evaluation view
![detaild_shouldGiveCecilaAccessToResource1](https://github.com/user-attachments/assets/1131f438-1073-4d21-9d9f-75ac3db7a1cc)

##### PIP result view
![attributes](https://github.com/user-attachments/assets/7ec6bb5e-4b47-4fbc-9af1-7e7a197374ac)

## Contact
Feel free to contact us at https://www.axiomatics.com if you have any questions
