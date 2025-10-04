FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
RUN apk add --no-cache wget

WORKDIR /app

COPY --chown=app:app app.jar /app/app.jar

#ENV JAVA_OPTS="-Xms256m -Xmx512m" \
#    SERVER_PORT=8080 \
#    TZ=Asia/Seoul
 
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Duser.timezone=Asia/Seoul" 

EXPOSE 8080
USER app

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
