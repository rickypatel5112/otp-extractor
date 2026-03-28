FROM maven:3.9.11-eclipse-temurin-17-alpine AS build
WORKDIR /app

# parent + service pom
COPY pom.xml .
COPY extract-otp/pom.xml service/pom.xml

# download deps
RUN mvn -f service/pom.xml -B -q -DskipTests dependency:go-offline

# copy source
COPY extract-otp/src service/src

# build
RUN mvn -f service/pom.xml clean package -DskipTests

# runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/service/target/*.jar extract-otp.jar

ENTRYPOINT ["java", "-jar", "extract-otp.jar"]
