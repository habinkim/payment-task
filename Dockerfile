FROM amazoncorretto:25 AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM amazoncorretto:25
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 18080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
