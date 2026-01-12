// src/main/java/com/itheima/ai/service/DocumentIngestionService.java

package com.itheima.ai.service;

import com.itheima.ai.utils.PdfTextExtractor;
import com.itheima.ai.utils.SimpleTextSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档摄入服务（PDF 上传 → 文本提取 → 切片 → 向量化）
 * 负责：
 * - 校验文件是否为 PDF
 * - 按页提取文本内容
 * - 对每页内容进行智能切片（chunk=500, overlap=50）
 * - 为每个文本块添加元数据（文件名 + 页码）
 * - 批量存入向量数据库（如 Qdrant）
 */
@Service
@RequiredArgsConstructor // Lombok 自动生成构造器注入 final 字段
public class DocumentIngestionService {

    // PDF 文本提取工具（基于 Apache PDFBox 实现）
    private final PdfTextExtractor pdfTextExtractor;

    // 向量存储接口（由 Spring AI 提供，如 QdrantVectorStore）
    private final VectorStore vectorStore;

    /**
     * 将用户上传的 PDF 文件解析并存入向量数据库
     *
     * @param file 用户通过 HTTP 上传的 MultipartFile 对象
     * @throws IllegalArgumentException 如果文件不是 PDF
     * 注意：其他所有异常（IO、解析、网络等）均被包装为 RuntimeException 抛出
     */
    public void ingestPdf(MultipartFile file) {
        try {
            // 1. 获取原始文件名（用于校验和元数据）
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                throw new IllegalArgumentException("仅支持 PDF 文件");
            }

            // 2. 调用工具类按页提取文本（可能抛出 IOException 或其他异常）
            List<String> pageTexts = pdfTextExtractor.extractPages(file);

            // 3. 初始化文本切片器（500字符一块，重叠50字符防止语义断裂）
            var splitter = new SimpleTextSplitter(300, 50);

            // 4. 存储最终要存入向量库的 Document 列表
            List<Document> documents = new ArrayList<>();

            // 5. 遍历每一页（pageNum 从 0 开始）
            for (int pageNum = 0; pageNum < pageTexts.size(); pageNum++) {
                String pageContent = pageTexts.get(pageNum);
                // 跳过空页或空白页
                if (pageContent == null || pageContent.isBlank()) {
                    continue;
                }

                // 6. 对当前页内容进行切片
                List<String> chunks = splitter.split(pageContent);

                // 7. 为每个 chunk 构造 Document 对象，并附加元数据
                for (String chunk : chunks) {
                    // 元数据包含：原始文件名 + 人类可读的页码（从1开始）
                    Map<String, Object> metadata = Map.of(
                            "source", filename,
                            "page", pageNum + 1
                    );
                    documents.add(new Document(chunk, metadata));
                }
            }

            // 8. 批量写入向量数据库（关键步骤，可能因网络/配置失败）
            vectorStore.add(documents);

        } catch (IllegalArgumentException e) {
            // 文件类型错误，直接重新抛出（Controller 可识别为 400 错误）
            throw e;
        } catch (Exception e) {
            // 捕获所有其他异常（包括 IOException、PDF 解析失败、向量库连接失败等）
            // 统一转换为 RuntimeException，避免方法签名污染，符合 Spring 风格
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }
}