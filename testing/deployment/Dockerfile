FROM eclipse-temurin:21-jre
ARG HOST
COPY ${HOST}/ /opt/app/
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["/bin/sh", "-c", "if [ -f /opt/app/server.jar ]; then exec java -jar /opt/app/server.jar; else exec /opt/app/bin/woge-reference-ktor; fi"]
