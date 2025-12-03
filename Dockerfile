# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:17-jammy AS runtime

WORKDIR /app

# Install Playwright/Chromium dependencies
RUN apt-get update && apt-get install -y \
    wget gnupg ca-certificates \
    libxkbcommon0 libatk1.0-0 libatk-bridge2.0-0 libcups2 \
    libgbm1 libasound2 libatspi2.0-0 libxcomposite1 \
    libxdamage1 libxfixes3 libxrandr2 libxrender1 \
    libpango-1.0-0 libcairo2 libnss3 libnspr4 \
    libxshmfence1 unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Chromium
RUN mkdir -p /ms-playwright && \
    wget -qO chromium.zip https://playwright.azureedge.net/builds/chromium/1131/chromium-linux.zip && \
    unzip chromium.zip -d /ms-playwright && \
    rm chromium.zip

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
