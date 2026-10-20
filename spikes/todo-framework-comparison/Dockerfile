FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
WORKDIR /opt/bench
COPY build/install/todo-framework-comparison/ ./
ENV PORT=8080 BENCH_BIND=0.0.0.0
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/opt/bench/bin/todo-framework-comparison"]
