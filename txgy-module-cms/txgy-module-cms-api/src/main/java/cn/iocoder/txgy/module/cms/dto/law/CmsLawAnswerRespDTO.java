package cn.iocoder.txgy.module.cms.dto.law;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 智能回答与可核验原文引用。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CmsLawAnswerRespDTO {

    private String question;
    private String answer;
    private String model;
    private boolean semanticAvailable;
    private boolean generated;
    private String warning;
    private List<Citation> citations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private int index;
        private Long articleId;
        private String sourceId;
        private String title;
        private String documentNo;
        private String articleNo;
        private String quote;
        private String sourceUrl;
        private Double score;
    }

}
