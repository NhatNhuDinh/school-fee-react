# Backend image. Build context is the repository root (see .gitlab-ci.yml),
# built by 174's own Docker (BuildKit), which keeps its layer cache and the
# cache mounts below from one pipeline to the next.
#
# Gradle's three working directories live in cache mounts (never part of the
# image):
#   /root/.gradle   Gradle itself, every dependency, and the classpath
#                   snapshot incremental compilation needs. A changed
#                   build.gradle downloads only the new jars.
#   /src/.gradle,
#   /src/build      what was compiled last time, so Gradle recompiles only the
#                   classes a change touches (it compares file contents, not
#                   timestamps).
# The ids carry the project name, because other projects build on the same
# Docker. sharing=locked makes two builds at once wait for each other. If a
# cache is ever wrong, `docker builder prune --filter type=exec.cachemount`
# empties them all and the next build is simply a full one.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/ .
# build/libs is emptied first: it sits in a cache mount, so a jar of an older
# version would otherwise still be there and match the copy below.
RUN --mount=type=cache,id=edupay-gradle-home,target=/root/.gradle,sharing=locked \
    --mount=type=cache,id=edupay-gradle-project,target=/src/.gradle,sharing=locked \
    --mount=type=cache,id=edupay-gradle-build,target=/src/build,sharing=locked \
    sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && rm -rf build/libs \
 && ./gradlew bootJar --no-daemon --max-workers=2 -x test -Dorg.gradle.jvmargs="-Xmx1024m" \
 && cp build/libs/school-fee-*.jar /app.jar
# Split the fat jar into its Spring Boot layers: the dependencies change only
# with build.gradle, so a build pushes, and 174 pulls, only the application
# layer instead of the whole jar.
RUN java -Djarmode=tools -jar /app.jar extract --layers --destination /extracted

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build --chown=app:app /extracted/dependencies/ ./
COPY --from=build --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
