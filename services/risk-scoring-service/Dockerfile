# syntax=docker/dockerfile:1.7
# ---- Build stage: full JDK 21, reactor-scoped build via the Maven Wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY libs/ libs/
COPY services/ services/
# -pl <module> -am builds only this service plus the modules it depends on.
RUN chmod +x mvnw && ./mvnw -B -ntp -pl services/risk-scoring-service -am -DskipTests clean package

# ---- Runtime stage: minimal JRE 21, non-root, OpenShift-friendly ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Create an unprivileged user in group 0 (root group) so OpenShift's arbitrary-UID model works:
# files are group-owned by 0 and group-writable; the effective UID may be randomised at runtime.
RUN useradd -u 1001 -g 0 -m -s /usr/sbin/nologin appuser
COPY --from=build /workspace/services/risk-scoring-service/target/risk-scoring-service.jar /app/app.jar
RUN chown -R 1001:0 /app && chmod -R g=u /app
USER 1001
EXPOSE 8085 9095
# Container-aware heap + low-pause GC for a latency-sensitive gRPC service.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
