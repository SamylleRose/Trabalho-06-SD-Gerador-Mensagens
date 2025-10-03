FROM maven:3.9.3-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY Main.java ./src/main/java/com/exemplo/Main.java
RUN mvn clean package -DskipTests

FROM openjdk:11-jdk-slim
WORKDIR /app
COPY --from=build /app/target/gerador-mensagens-1.0.jar ./gerador-mensagens.jar
EXPOSE 8000

# REMOVA AS LINHAS 'COPY' DAS IMAGENS DAQUI
# COPY base-rosto/images/validation/*/*.jpg ./base-rosto/  <-- REMOVER
# COPY base-brasao/base-brasao/logos/*/*.png ./base-brasao/ <-- REMOVER

CMD ["java", "-jar", "gerador-mensagens.jar"]