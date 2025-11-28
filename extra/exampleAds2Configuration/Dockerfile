FROM eclipse-temurin:21-jre

EXPOSE 8080 8082

COPY . .

# Create a symlink to the JAR file without version number for easier access
RUN target=$(ls access-decision-service-*.jar 2>/dev/null | head -n1) && \
    [ -n "$target" ] && \
    ln -s "$target" access-decision-service.jar

# Make sure executable and correct line endings
RUN chmod +x ./startService.sh
RUN sed -i -e 's/\r$//' startService.sh

ENTRYPOINT ["sh","./startService.sh"]