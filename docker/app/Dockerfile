# ✅ 亚马逊官方Java17镜像 ✅ 天生支持AMD64+ARM64 ✅ 无权限 ✅ 全球拉取秒成功 ✅ 绝对不会not found
# ✅ 体积小(180MB) ✅ 兼容SpringBoot所有项目 ✅ 所有人下载都能正常运行
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY ./target/heima-ai.jar ./app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]