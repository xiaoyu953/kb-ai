package com.itheima.ai.utils;

import java.util.ArrayList;
import java.util.List;

public class SimpleTextSplitter {

    private final int chunkSize;
    private final int chunkOverlap;
    private final int maxChunks; // 新增：防止生成过多 chunk

    public SimpleTextSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, 10_000); // 默认最多 1 万个 chunk
    }

    // 私有构造器支持最大限制（可选）
    private SimpleTextSplitter(int chunkSize, int chunkOverlap, int maxChunks) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        if (chunkOverlap < 0) throw new IllegalArgumentException("chunkOverlap must be >= 0");
        if (chunkOverlap >= chunkSize) throw new IllegalArgumentException("chunkOverlap must be < chunkSize");
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.maxChunks = maxChunks;
    }

    public List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int len = text.length();

        while (start < len && chunks.size() < maxChunks) {
            int end = Math.min(start + chunkSize, len);
            chunks.add(text.substring(start, end));

            if (end == len) {
                break; // 到达末尾
            }

            // 计算下一次起始位置
            int nextStart = end - chunkOverlap;
            // 关键修复：防止回退到已处理位置或死循环
            if (nextStart <= start) {
                nextStart = end; // 至少前进到当前结束位置
            }
            start = nextStart;
        }

        return chunks;
    }
}