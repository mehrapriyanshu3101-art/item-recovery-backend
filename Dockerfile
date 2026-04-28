# Step 1: Use a reliable Maven image with Java 17 to build the application
FROM maven:3.8.5-eclipse-temurin-17 AS build

# Copy your source code and pom.xml into the container
COPY . .

# Run the Maven build to create the executable JAR file
RUN mvn clean package -DskipTests

# Step 2: Use a lightweight Java Runtime Environment (JRE) to run the app
FROM eclipse-temurin:17-jre-focal

# Copy the generated JAR file from the build stage to the final image
COPY --from=build /target/*.jar app.jar

# Tell Render to listen on port 8080
EXPOSE 8080

# The command to start your Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
