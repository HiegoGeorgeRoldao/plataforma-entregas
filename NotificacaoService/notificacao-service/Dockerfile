FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --chown=spring:spring target/notificacao-service-0.0.1-SNAPSHOT.jar app.jar
USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
