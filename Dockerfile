# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom and sources
COPY pom.xml .
COPY src ./src

# Build the application (skip tests for speed)
RUN mvn -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/llms-txt-generator-0.0.1-SNAPSHOT.jar app.jar

# Render will set PORT; Spring Boot reads it via server.port=${PORT:8080}
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

