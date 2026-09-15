package cn.iocoder.txgy.module.cms.dto.law;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 基于已入库法规原文的智能问答请求。 */
@Data
public class CmsLawAnswerReqDTO {

    @NotBlank
    @Size(max = 500)
    private String question;

    @Min(1)
    @Max(20)
    private Integer topK;

}
