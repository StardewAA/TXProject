package cn.iocoder.txgy.module.cms.dto.law;

import cn.iocoder.txgy.module.cms.enums.CmsSearchScopeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 基于已入库法规原文的智能问答请求。 */
@Data
public class CmsLawAnswerReqDTO {

    @NotBlank
    @Size(max = 500)
    private String question;

    @Min(1)
    @Max(20)
    private Integer topK;

    /** AUTO/ALL/QA/LAW/FINANCE_POLICY；AUTO 会识别法规、标准及惠企金融政策特征。 */
    private CmsSearchScopeEnum searchScope = CmsSearchScopeEnum.AUTO;

    /** 最近几轮问答，由客户端随追问携带；后端保持无会话状态。 */
    @Valid
    @Size(max = 6)
    private List<HistoryItem> history;

    @Data
    public static class HistoryItem {

        @NotBlank
        @Size(max = 500)
        private String question;

        @NotBlank
        @Size(max = 4000)
        private String answer;
    }

}
