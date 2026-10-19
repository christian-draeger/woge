FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/christian-draeger/woge" \
      org.opencontainers.image.title="Woge Todo" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="Apache-2.0"
WORKDIR /opt/app
COPY build/libs/woge-todo-0.1.0.jar application.jar
USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/opt/app/application.jar"]
