package com.itheima.ai.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor {

    /**
     * 使用 PDFBox 按页提取 PDF 文本
     *
     * @param file 上传的 PDF 文件
     * @return 每一页的文本列表（索引0 = 第1页）
     * @throws IOException 文件读取或解析失败
     */
    public List<String> extractPages(MultipartFile file) throws Exception {
        List<String> pages = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            // 将 InputStream 转为 RandomAccessRead（PDFBox 3.x 推荐方式）
            byte[] bytes = is.readAllBytes();
            try (var pdfDoc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
                int totalPages = pdfDoc.getNumberOfPages();

                if (totalPages == 0) {
                    return pages; //空PDF
                }

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true); //提升中文段落顺序准确性

                for (int i = 0; i < totalPages; i++) {
                    // getText(pdf, startPage, endPage) 页码从1开始
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String text = stripper.getText(pdfDoc).trim();
                    pages.add(text);
                }
            }
        } catch (IOException e) {
            throw new IOException("PDF 解析失败：请检查文件是否损坏或加密", e);
        }
        return pages;
    }
}
