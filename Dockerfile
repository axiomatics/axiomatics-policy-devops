# ADS, its libraries and configuration: everything but the domain
FROM docker.io/library/eclipse-temurin:25-jre AS binaries
WORKDIR /binaries
COPY . .
# Remove the domain, link the JAR without version number, make startService.sh executable with Unix line endings
RUN rm -f domain.yaml && \
    target=$(ls access-decision-service-*.jar | head -n1) && \
    ln -s "$target" access-decision-service.jar && \
    sed -i -e 's/\r$//' startService.sh && \
    chmod +x startService.sh

FROM docker.io/library/eclipse-temurin:25-jre
EXPOSE 8080 8082
WORKDIR /opt/ads
COPY --from=binaries /binaries/ ./
# Last, in its own small layer: when only the domain changes, the layers above are reused from the build cache
COPY domain.yaml .
# Run ADS as a non-root user; the files stay owned by root, so ADS cannot change them
USER 10001
ENTRYPOINT ["sh","./startService.sh"]
