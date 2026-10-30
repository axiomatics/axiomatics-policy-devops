This directory contains the configuration for the legacy Access Decision Service 1 (ADS 1): a `Dockerfile` and a
`deployment.yaml`. The project itself is set up for the current ADS (the root `Dockerfile` and `deployment.yaml`, and
`startService.sh` and `logback-custom.xml` in `src/extra`).

**ADS 1 support is deprecated and will be removed in APD 27.1**, together with the tasks `runAdsV1` and
`buildAdsDockerImage`. Upgrade to the current ADS, see the [ADS migration guide](https://docs.axiomatics.com/access-decision-service/installation/migration/).

**How to use the ADS 1 configuration**

1. Replace the root `deployment.yaml` with the one in this directory.
2. Replace the root `Dockerfile` with the one in this directory.
3. Copy your ADS 1 license to `license/axiomatics_PDP.license`, and set `licenseFile "license/axiomatics_PDP.license"`
   in the `alfa { }` section of `build.gradle`.
4. Run ADS 1 locally with `runAdsV1`, or build a Docker image with `buildAdsDockerImage`.
