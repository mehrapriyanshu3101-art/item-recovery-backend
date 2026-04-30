# Stage 1: Build the application
FROM maven:3.8.5-eclipse-temurin-17 AS build

# Create a safe working directory inside the container
WORKDIR /app

# Copy only the necessary build files first
COPY pom.xml .
COPY src ./src

# Build the project
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-focal

# Create the final working directory
WORKDIR /app

# Copy the built JAR file from the 'build' stage into this new stage
COPY --from=build /app/target/*.jar app.jar

# Expose the standard Spring Boot port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
