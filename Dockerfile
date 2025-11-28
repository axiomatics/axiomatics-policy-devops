#This is an ADS v1 example Dockerfile
#For ADS v2 please see example in extra/exampleAds2Configuration folder

FROM eclipse-temurin:17-jre-alpine

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