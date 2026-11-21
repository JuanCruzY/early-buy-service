FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN sed -i 's/\r$//' gradlew
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S -g 10001 earlybuy && adduser -S -u 10001 -G earlybuy earlybuy
COPY --from=build /workspace/build/libs/early-buy-service.jar /app/early-buy-service.jar
USER earlybuy
EXPOSE 3005
ENTRYPOINT ["java", "-jar", "/app/early-buy-service.jar"]
