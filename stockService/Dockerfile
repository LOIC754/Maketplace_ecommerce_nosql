# Étape 1 : compilation avec Gradle (inutile d'installer Gradle ou Java sur le PC)
FROM gradle:8.6-jdk21 AS builder
WORKDIR /home/gradle/project
COPY settings.gradle build.gradle ./
COPY stockService ./stockService
RUN gradle :stockService:bootJar -x test --no-daemon

# Étape 2 : image d'exécution légère, sans les outils de compilation
FROM eclipse-temurin:21-jre
COPY --from=builder /home/gradle/project/stockService/build/libs/*.jar /app/stock-service.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/stock-service.jar"]
