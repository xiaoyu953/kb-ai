# 终极零失败镜像：官方openjdk17，无权限、双架构、稳定到极致，适配M3+服务器
FROM openjdk:17-slim
WORKDIR /app
COPY ./target/heima-ai.jar ./app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]