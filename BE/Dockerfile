FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew

COPY src ./src
COPY config ./config
COPY .rules ./.rules

RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
