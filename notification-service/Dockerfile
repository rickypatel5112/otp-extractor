FROM maven:3.9.11-eclipse-temurin-17-alpine AS build
LABEL authors="ricky"

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app
COPY --from=build /app/target/*.jar notification.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "notification.jar"]
