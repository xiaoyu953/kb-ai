# ✅ 这行是「买原料」：下载一个基础的Java运行环境（联网下载！）
FROM amazoncorretto:17-alpine
# ✅ 这行是「准备工作台」：在镜像里创建一个叫/app的文件夹，以后所有操作都在这个文件夹里做（本地操作）
WORKDIR /app
# ✅ 这行是「放蛋糕胚」：把你本地Maven打包好的jar包，复制到镜像的/app文件夹里（本地操作，不联网）
COPY ./target/heima-ai.jar ./app.jar
# ✅ 这行是「留个窗口」：告诉Docker，这个镜像运行的项目会用到8088端口，方便外部访问（只是声明，不做实际操作）
EXPOSE 8088
# ✅ 这行是「启动蛋糕机」：镜像运行起来后，自动执行的命令 → 用Java运行这个jar包，启动你的项目（本地操作）
ENTRYPOINT ["java", "-jar", "app.jar"]