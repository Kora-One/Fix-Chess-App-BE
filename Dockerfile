# ==========================================
# STAGE 1: Build the Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy only the pom.xml first to cache dependencies
COPY pom.xml .
# Download dependencies (this makes future builds faster)
RUN mvn dependency:go-offline

# Copy the actual source code
COPY src ./src

# Build the .jar file inside the container (bypassing Windows/IntelliJ completely)
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Run the Application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy the generated .jar from Stage 1 into this final, lightweight container
COPY --from=builder /app/target/*.jar app.jar

# Expose the web port
EXPOSE 8080

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]