# =====================================================
# Jade Platform · 多阶段构建（JVM 版） · jade-admin
# =====================================================
# 用法：
#   docker build -f backend/jade-admin/Dockerfile -t jade-admin:1.0.0 backend/
#   docker run -p 8080:8080 jade-admin:1.0.0

# ============ Stage 1: 构建 ============
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 用阿里云镜像加速（国内场景）
COPY jade-demo/settings-docker.xml /root/.m2/settings.xml

# 先拷贝所有模块的 pom（最大化缓存）
COPY pom.xml ./
COPY jade-dependencies/pom.xml jade-dependencies/
COPY jade-common/pom.xml jade-common/
COPY jade-web/pom.xml jade-web/
COPY jade-security/pom.xml jade-security/
COPY jade-log/pom.xml jade-log/
COPY jade-redis/pom.xml jade-redis/
COPY jade-oss/pom.xml jade-oss/
COPY jade-codegen/pom.xml jade-codegen/
COPY jade-admin/pom.xml jade-admin/

# 预下载依赖
RUN mvn -B -q dependency:go-offline || true

# 拷贝源码并构建
COPY . .
RUN mvn -B -DskipTests -pl jade-admin -am package

# ============ Stage 2: 运行时 ============
FROM eclipse-temurin:21-jre
WORKDIR /app

# 非 root 运行
RUN groupadd -r jade && useradd -r -g jade jade \
    && mkdir -p /app/logs && chown -R jade:jade /app

COPY --from=builder /build/jade-admin/target/quarkus-app/lib/        /app/lib/
COPY --from=builder /build/jade-admin/target/quarkus-app/*.jar      /app/
COPY --from=builder /build/jade-admin/target/quarkus-app/app/       /app/app/
COPY --from=builder /build/jade-admin/target/quarkus-app/quarkus/   /app/quarkus/

USER jade

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
ENV JAVA_APP_JAR="/app/quarkus-run.jar"

ENTRYPOINT [ "sh", "-c", "exec java $JAVA_OPTS -jar $JAVA_APP_JAR" ]

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -qO- http://localhost:8080/q/health/live || exit 1
