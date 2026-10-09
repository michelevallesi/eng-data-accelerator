# syntax=docker/dockerfile:1
FROM docker.io/library/eclipse-temurin:21-jre
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="eda-query-service"
LABEL org.opencontainers.image.revision=${GIT_SHA}
WORKDIR /app
COPY target/eda-query-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
