package com.itheima.ai.utils;

import com.itheima.ai.entity.dto.RagResponse;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档处理工具类
 */
public class DocumentUtils {

    /**
     * 安全提取页码数字（处理不同类型的 metadata 值）
     *
     * @param pageObj 可能是 Integer / Long / String
     * @return 有效页码，失败时默认返回 1
     */
    public static int extractPageNumber(Object pageObj) {
        if (pageObj == null) return 1;
        if (pageObj instanceof Number) {
            return ((Number) pageObj).intValue();
        }
        if (pageObj instanceof String) {
            try {
                return Integer.parseInt((String) pageObj);
            } catch (NumberFormatException ignored) {
                // 解析失败，返回默认值
            }
        }
        return 1; // 默认第 1 页
    }

    /**
     * 构建带编号的上下文，并收集真实引用信息
     *
     * @param similarDocs 相似文档列表
     * @param contextBuilder 上下文构建器
     * @param validCitations 有效引用列表
     */
    public static void buildContextAndCitations(List<Document> similarDocs, 
                                               StringBuilder contextBuilder, 
                                               List<RagResponse.Citation> validCitations) {
        for (int i = 0; i < similarDocs.size(); i++) {
            Document doc = similarDocs.get(i);
            String content = doc.getText(); // 注意：Spring AI 中是 getText()，不是 getContent()
            if (content == null || content.trim().isEmpty()) continue;

            String source = (String) doc.getMetadata().getOrDefault("source", "unknown.pdf");
            int page = extractPageNumber(doc.getMetadata().get("page"));

            // 正确写法：
            contextBuilder.append(String.format("[%d] %s\n", i + 1, content.trim()));
            validCitations.add(new RagResponse.Citation(source, page));
        }
    }

    /**
     * 转义文本中的特殊字符，防止格式化错误
     *
     * @param text 需要转义的文本
     * @return 转义后的文本
     */
    public static String escapeText(String text) {
        if (text == null) return "";
        return text.trim().replace("%", "%%");
    }
}