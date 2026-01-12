package com.itheima.ai.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {

    private String answer;
    private List<Citation> citations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String source;
        private Integer page;
    }
}