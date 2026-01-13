# 官方公共镜像 + 无需权限 + 原生支持ARM64/AMD64双架构 + 国内可拉取 + 体积小(90MB)
FROM bellsoft/liberica-openjdk:17-alpine
WORKDIR /app
COPY ./target/heima-ai.jar ./app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]