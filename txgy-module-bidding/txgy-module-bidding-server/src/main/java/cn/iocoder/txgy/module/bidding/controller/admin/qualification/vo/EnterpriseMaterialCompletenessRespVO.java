package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 企业材料完备性自查结论（身份类/资质类/实力类）。
 *
 * <p>规范出处：用户业务口径——《资质管理规范文件》目录三分类材料（企业身份证明、企业资质证明、企业实力证明），
 * "覆盖招投标全流程"；《电子招标投标办法》（20号令）第42条（企业资质证书、从业人员资格等应如实提交）；
 * 《建筑业企业资质管理规定》（住建部令第22号）+《安全生产许可证条例》（国务院令第397号）（安许必备）。
 * <p>为什么这样写：材料是否齐备、是否在有效期内，过去靠人工翻台账（Excel 最容易漏）；
 * 这里把"哪类材料、在哪张表核验、缺什么"固化成结构化结论，供投标前一次性自查。
 * <p>业务目标：投标/申报前一眼看出缺哪份材料、哪份已过期，避免因材料问题被资格审查否决。
 */
@Schema(description = "管理后台 - 企业材料完备性自查结论")
@Data
public class EnterpriseMaterialCompletenessRespVO {

    @Schema(description = "归属企业")
    private Long entId;
    @Schema(description = "总体结论：PASS 齐备/RISK 部分缺失或临近到期/FAIL 必备材料缺失")
    private String result;
    @Schema(description = "必备材料总数")
    private Integer requiredCount;
    @Schema(description = "齐备数")
    private Integer readyCount;
    @Schema(description = "缺失清单（材料名称）")
    private List<String> missingItems;
    @Schema(description = "临近到期清单（材料名称 + 到期日）")
    private List<String> expiringItems;
    @Schema(description = "逐项明细")
    private List<MaterialItem> items;

    /** 单项材料核验结果。 */
    @Schema(description = "单项材料核验结果")
    @Data
    public static class MaterialItem {

        @Schema(description = "材料类型 code（EnterpriseMaterialTypeEnum）")
        private String materialType;
        @Schema(description = "材料类型名称")
        private String materialLabel;
        @Schema(description = "材料大类：IDENTITY/QUALIFICATION/STRENGTH/OTHER")
        private String category;
        @Schema(description = "是否必备")
        private Boolean mandatory;
        @Schema(description = "状态：READY 齐备/EXPIRING 临近到期/MISSING 缺失/EXPIRED 已过期")
        private String status;
        @Schema(description = "匹配到的记录主键（证照/资质/安许/业绩）")
        private Long refId;
        @Schema(description = "匹配到的名称/编号")
        private String refName;
        @Schema(description = "有效期止（可空）")
        private String validTo;
        @Schema(description = "说明（缺失原因/风险提示）")
        private String message;
    }
}