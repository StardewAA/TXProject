package cn.iocoder.txgy.module.cms.dto.law;

import cn.iocoder.txgy.module.cms.enums.CmsLawStatusEnum;
import lombok.Data;

import java.time.LocalDate;

/** 法规条款命中结果。保留原文来源和版本，不以模型生成内容替代原文。 */
@Data
public class CmsLawSearchRespDTO {

    private Long lawId;
    private Long articleId;
    private String sourceId;
    private String title;
    private String documentNo;
    private String articleNo;
    private String content;
    private String version;
    private String issuingAuthority;
    private String sourceUrl;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private CmsLawStatusEnum status;
    private Double score;

}
