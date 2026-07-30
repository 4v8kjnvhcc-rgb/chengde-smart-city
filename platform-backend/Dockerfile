# Multi-stage: build jar then run on JRE 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
# 不用 -q：失败时要能看到编译/拉依赖错误
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser \
  && apt-get update && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/platform-backend-*.jar /app/app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1536m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
