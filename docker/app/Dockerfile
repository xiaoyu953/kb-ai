# docker/app/Dockerfile
# 第一阶段：构建 JAR
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.javadoc.skip=true
# 生成 target/heima-ai.jar

# 第二阶段：运行
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/heima-ai.jar ./app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]