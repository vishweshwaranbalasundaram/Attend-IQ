FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
COPY --from=build /workspace/target/java-attendance-1.0.0.jar /app/attendiq.jar
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar /app/attendiq.jar"]
