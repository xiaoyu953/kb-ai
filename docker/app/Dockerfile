# 完美适配ARM64(Apple M3) + AMD64(云服务器)，阿里国内镜像源，拉取超快，彻底解决架构报错
FROM registry.aliyuncs.com/ibyte/java:17-jre-alpine
WORKDIR /app
COPY ./target/heima-ai.jar ./app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]