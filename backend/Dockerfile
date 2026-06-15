# --- Build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Flags wagon: toleram redes com inspeção TLS (cert intercetado) — sem efeito
# em redes normais. Necessário porque o resolver nativo do Maven 3.9 não
# tem opção de ignorar certificados.
ENV MAVEN_ARGS="-Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"
COPY pom.xml .
# Cache de dependências (camada separada do código-fonte)
RUN mvn -q $MAVEN_ARGS dependency:go-offline
COPY src ./src
RUN mvn -q $MAVEN_ARGS clean package -DskipTests

# --- Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
