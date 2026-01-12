package com.itheima.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC配置类
 * 配置Web相关的功能，如跨域资源共享（CORS）
 *
 * CORS说明：
 * - Cross-Origin Resource Sharing（跨域资源共享）
 * - 用于解决浏览器的同源策略限制，允许不同域名下的前端访问后端API
 *
 * 典型使用场景：
 * - 前端应用部署在域名A，后端API部署在域名B
 * - 浏览器出于安全考虑默认禁止跨域请求
 * - 通过配置CORS允许特定来源的跨域访问
 */
@Configuration
public class MvcConfiguration implements WebMvcConfigurer {

    /**
     * 配置跨域请求策略
     * addCorsMappings()方法用于定义哪些路径需要允许跨域访问
     *
     * 配置项说明：
     * 1. addMapping("/**")
     *    - 匹配所有URL路径
     *    - "/" 表示根路径，"**" 表示任意深度的子路径
     *    - 效果：对项目中的所有接口都启用跨域支持
     *
     * 2. allowedOrigins("*")
     *    - 允许跨域访问的来源域名
     *    - "*" 表示允许任何域名访问（生产环境建议指定具体域名）
     *    - 示例："http://localhost:3000" 只允许本地3000端口访问
     *
     * 3. allowedMethods()
     *    - 允许的HTTP请求方法
     *    - GET：获取资源
     *    - POST：创建资源
     *    - PUT：更新资源
     *    - DELETE：删除资源
     *    - OPTIONS：预检请求（浏览器自动发送检查是否允许跨域）
     *
     * 4. allowedHeaders()
     *    - 允许在请求中携带的HTTP头
     *    - "*" 表示允许任何头信息
     *    - 常见头：Content-Type、Authorization、Token等
     *
     * 5. exposedHeaders()
     *    - 允许浏览器从响应中读取的头信息
     *    - Content-Disposition：用于文件下载场景，告诉浏览器文件名
     *
     * @param registry CORS注册器，用于添加跨域规则
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置所有路径都允许跨域访问
        registry.addMapping("/**")
                // 允许所有来源的跨域请求（生产环境请替换为具体域名）
                .allowedOrigins("*")
                // 允许常见的HTTP方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许所有请求头
                .allowedHeaders("*")
                // 暴露Content-Disposition头（用于文件下载）
                .exposedHeaders("Content-Disposition");
    }
}
