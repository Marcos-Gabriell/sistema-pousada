# Dockerfile

# 1) build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# baixa dependências primeiro (cache)
RUN mvn -q -e -DskipTests dependency:go-offline
# agora copia o código
COPY src ./src
RUN mvn -q -DskipTests package

# 2) runtime (imagem leve)
FROM eclipse-temurin:21-jre
WORKDIR /app
# copia o jar gerado
COPY --from=build /app/target/*.jar app.jar

# porta da sua api
EXPOSE 8080

# perfil padrão = dev (pode sobrescrever no docker-compose)
ENV SPRING_PROFILE=dev

ENTRYPOINT ["java", "-jar", "app.jar"]
