# Build with yudao-cloud as the Docker context:
# docker build -f yudao-server/Dockerfile -t furniture-erp-backend .
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
COPY . .

RUN mvn -B -ntp -pl yudao-server -am -DskipTests package \
    && test -s yudao-server/target/yudao-server.jar

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S yudao && adduser -S -G yudao yudao

WORKDIR /opt/yudao
COPY --from=build --chown=yudao:yudao /workspace/yudao-server/target/yudao-server.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx2048m -Djava.security.egd=file:/dev/./urandom"
ENV ARGS="--spring.profiles.active=prod"

USER yudao

EXPOSE 48080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $ARGS"]
