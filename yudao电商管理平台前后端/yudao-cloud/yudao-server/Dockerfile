# Build with yudao-cloud as the Docker context:
# docker build -f yudao-server/Dockerfile -t furniture-erp-backend .
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
COPY . .

RUN mvn -B -ntp -pl yudao-server -am -DskipTests package \
    && test -s yudao-server/target/yudao-server.jar \
    && cp yudao-server/target/yudao-server.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S yudao && adduser -S -G yudao yudao

WORKDIR /opt/yudao
# Keep stable dependencies reusable when only application modules change.
COPY --from=build --chown=yudao:yudao /workspace/extracted/dependencies/ ./
COPY --from=build --chown=yudao:yudao /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=yudao:yudao /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=yudao:yudao /workspace/extracted/application/ ./
LABEL io.oakved.image.layout="spring-boot-tools-v1"

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx2048m -Djava.security.egd=file:/dev/./urandom"
ENV ARGS="--spring.profiles.active=prod"

USER yudao

EXPOSE 48080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $ARGS"]
