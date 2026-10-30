# ADS 1 example Dockerfile, for the deprecated task buildAdsDockerImage. See README.md in this folder.

FROM eclipse-temurin:25-jre-alpine

EXPOSE 8081 8082
WORKDIR "ads"

WORKDIR "lib"
COPY lib ./

WORKDIR "../app"
RUN mv  ../lib/access-decision-service*.jar .

WORKDIR "../domain"
COPY domain ./

ENTRYPOINT ["java", "-cp", "../app/*:../lib/*", "com.axiomatics.ads.App"]
CMD ["server", "deployment.yaml"]