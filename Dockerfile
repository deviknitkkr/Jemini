# Build stage
FROM maven AS builder
VOLUME /root/.m2
WORKDIR /app
COPY pom.xml .
COPY src/ ./src/
COPY target/ ./target/
#RUN mvn package -DskipTests

# Runtime stage
FROM maven
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

