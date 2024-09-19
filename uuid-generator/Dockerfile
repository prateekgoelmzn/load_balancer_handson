FROM gradle:jdk17 AS build

WORKDIR /app

COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY gradlew .

COPY src src

RUN ./gradlew clean build

FROM amazoncorretto:17.0.12-alpine3.20

WORKDIR /app

COPY --from=build /app/build/libs/uuid-generator-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]