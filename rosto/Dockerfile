
FROM maven:3.9.3-eclipse-temurin-11 AS build
WORKDIR /app


COPY pom.xml .


RUN mvn dependency:go-offline


COPY Main.java ./src/main/java/com/exemplo/Main.java


RUN mvn clean package -DskipTests


FROM openjdk:11-jdk-slim
WORKDIR /app


COPY --from=build /app/target/face-consumer-1.0.jar ./face-consumer.jar


COPY model.h5 .
EXPOSE 8000
CMD ["java", "-jar", "face-consumer.jar"]