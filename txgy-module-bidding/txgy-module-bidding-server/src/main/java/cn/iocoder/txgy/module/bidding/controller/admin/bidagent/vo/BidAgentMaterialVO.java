package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

// [规范依据]《招投标模块开发规范》§3.1（自建 VO 放 controller/admin/<domain>/vo，驼峰 + 常规类型）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "标书智能体 - 材料台账 VO")
public class BidAgentMaterialVO {

    @Schema(description = "材料分页入参（BidAgentMaterialPageReqVO）")
    @Data
    public static class PageReqVO extends PageParam {
        @Schema(description = "材料角色：TENDER/FACT/TEMPLATE/PRICING")
        private String docType;
        @Schema(description = "所属标段编号")
        private String bidSectionCode;
        @Schema(description = "所属项目（PRICING 报价数据）")
        private Long projectId;
        @Schema(description = "入库状态：0 待入库 1 已入库 2 失败")
        private Integer indexStatus;
        @Schema(description = "材料名称（模糊）")
        private String name;
    }

    @Schema(description = "材料登记入参（BidAgentMaterialSaveReqVO）")
    @Data
    public static class SaveReqVO {
        @Schema(description = "材料角色：TENDER/FACT/TEMPLATE/PRICING", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "材料角色不能为空")
        private String docType;
        @Schema(description = "材料名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "材料名称不能为空")
        private String name;
        @Schema(description = "所属标段编号（TENDER 必填，其余可空）")
        private String bidSectionCode;
        @Schema(description = "所属项目（PRICING 报价数据必填，其余可空）")
        private Long projectId;
        @Schema(description = "infra 文件编号（上传接口返回的 fileId）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "文件编号不能为空")
        private Long fileId;
        @Schema(description = "文件访问地址（仅用于取内容入库，不作为一致性依据）")
        private String fileUrl;
        @Schema(description = "原始文件名")
        private String fileName;
        @Schema(description = "文件大小（字节）")
        private Long fileSize;
        @Schema(description = "有效期（FACT 类资质）")
        private LocalDate validUntil;
        @Schema(description = "备注")
        private String remark;
    }

    @Schema(description = "材料出参（BidAgentMaterialRespVO）")
    @Data
    public static class RespVO {
        private Long id;
        private String docType;
        private String name;
        private String bidSectionCode;
        private Long projectId;
        private Long fileId;
        /** 文件访问地址（仅用于即时预览，不作为一致性依据；落库关联键是 fileId） */
        private String fileUrl;
        private String fileName;
        /** 文件大小（字节） */
        private Long fileSize;
        private Integer indexStatus;
        private Integer segmentCount;
        private String indexMessage;
        private LocalDate validUntil;
        private String remark;
        /** 创建时间（epoch 毫秒） */
        private LocalDateTime createTime;
    }
}
