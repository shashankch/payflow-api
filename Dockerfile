FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

# Copy the fat jar produced by the Spring Boot maven plugin
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
