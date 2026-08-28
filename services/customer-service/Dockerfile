# syntax=docker/dockerfile:1.7
# ---- Build stage: full JDK 21, reactor-scoped build via the Maven Wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY libs/ libs/
COPY services/ services/
# A clone made on Windows with core.autocrlf=true carries CRLF; strip the CRs so
# the POSIX shebang in mvnw resolves and the wrapper can parse its properties.
# .gitattributes prevents this at the source — this keeps older clones building.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw
# -pl <module> -am builds only this service plus the modules it depends on.
# The BuildKit cache mount is shared by all nine image builds, so the dependency
# tree is fetched from Maven Central/Confluent once rather than nine times.
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    ./mvnw -B -ntp -pl services/customer-service -am -DskipTests clean package

# ---- Runtime stage: minimal JRE 21, non-root, OpenShift-friendly ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Unprivileged user in group 0 so OpenShift's arbitrary-UID model works (group-owned, group-writable).
RUN useradd -u 1001 -g 0 -m -s /usr/sbin/nologin appuser
COPY --from=build /workspace/services/customer-service/target/customer-service.jar /app/app.jar
RUN chown -R 1001:0 /app && chmod -R g=u /app
USER 1001
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
