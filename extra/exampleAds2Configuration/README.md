This directory contains an in-depth example illustrating how to configure ADS 2, logging, and a sample Dockerfile. These materials can serve as a starting point for creating a configuration suitable for a production deployment.

**Noteworthy features**

- Examples for setting up environment variables used in ADS 2 configuration.
- Separate servers for ADS and probes. The probe endpoints lack authentication, which is the expected configuration for the control plane.
- Audit logging as JSON objects, with examples on how to include MDC/OpenTelemetry tracing and how to customize the logs with your own fields.

- **How to use the example deployment**
- 
1. Replace the root `deployment.yaml` with the provided one provided in this example.
2. Replace the root `Dockerfile` with the provided one provided in this example.
3. Copy `logback-custom.xml` and  `startService.sh` into the `/src/extra` directory.
4. Execute `installDeploymentDist` to stage your binaries and authorization domain in `build/install/deployment`.
   From this point you can build your image.


   **NOTE:** To fully utilize the OpenTelemetry aspects of this example, you must instrument the JRE with your chosen OpenTelemetry agent. For more details, refer to the official documentation (https://opentelemetry.io/docs/instrumentation/java/automatic/) or contact your logging platform team.