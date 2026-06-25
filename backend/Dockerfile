FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
ARG MAVEN_ARGS=""

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B -U dependency:go-offline
COPY src ./src
RUN mvn -B $MAVEN_ARGS clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]