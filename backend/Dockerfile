FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/target/lacr-loan-closure-reconciliation-1.0.0.jar /app/app.jar

EXPOSE 8012

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
