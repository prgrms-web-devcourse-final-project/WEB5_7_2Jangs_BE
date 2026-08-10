# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY build.gradle* settings.gradle* ./
RUN chmod +x ./gradlew

RUN ./gradlew --no-daemon dependencies || true

COPY . .

RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
RUN apk add --no-cache wget

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/build/libs/*.jar /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Duser.timezone=Asia/Seoul" 

EXPOSE 8080
USER app

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
