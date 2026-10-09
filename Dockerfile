# Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system ecommerce && useradd --system --gid ecommerce ecommerce
COPY --from=build --chown=ecommerce:ecommerce /app/target/*.jar app.jar
USER ecommerce
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
