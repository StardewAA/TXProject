package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "标书智能体 - 智能体配置 VO")
public class BidAgentProfileVO {

    @Schema(description = "智能体配置入参（BidAgentProfileSaveReqVO）")
    @Data
    public static class SaveReqVO {
        @Schema(description = "编号（新增留空）")
        private Long id;
        @Schema(description = "任务类型：PARSE/OUTLINE/DRAFT/FILL/REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "任务类型不能为空")
        private String taskType;
        @Schema(description = "智能体名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "智能体名称不能为空")
        private String name;
        @Schema(description = "一句话说明")
        private String description;
        @Schema(description = "系统提示词")
        private String systemPrompt;
        @Schema(description = "模型名（空=平台默认）")
        private String model;
        @Schema(description = "温度（空=平台默认）")
        private BigDecimal temperature;
        @Schema(description = "单条回复最大 token（空=用全局云端模型配置）")
        private Integer maxTokens;
        @Schema(description = "默认挂载的知识库角色，逗号分隔，如 TENDER,FACT")
        private String knowledgeRoles;
        @Schema(description = "内置工具白名单（逗号分隔，空=全部允许）")
        private String toolWhitelist;
        @Schema(description = "是否自动挂载推荐技能：1 是 0 否")
        private Integer skillAutoAttach;
        @Schema(description = "排序")
        private Integer sort;
        @Schema(description = "状态：0 开启 1 停用")
        @NotNull(message = "状态不能为空")
        private Integer status;
        @Schema(description = "备注")
        private String remark;
    }

    @Schema(description = "智能体配置出参（BidAgentProfileRespVO）")
    @Data
    public static class RespVO {
        private Long id;
        private String taskType;
        private String name;
        private String description;
        private String systemPrompt;
        private String model;
        private BigDecimal temperature;
        private Integer maxTokens;
        /** 默认挂载的知识库角色（逗号分隔原样返回，另有数组形态给前端直接渲染） */
        private String knowledgeRoles;
        /** 内置工具白名单（逗号分隔） */
        private String toolWhitelist;
        /** 是否自动挂载推荐技能：1 是 0 否 */
        private Integer skillAutoAttach;
        /** 知识库角色数组（由 knowledgeRoles 拆分，避免前端再拆字符串） */
        private List<String> knowledgeRoleList;
        /** 是否后台可编辑：1 可编辑 0 只读（锁机制保留） */
        private Integer editable;
        private Integer sort;
        private Integer status;
        private String remark;
        private LocalDateTime createTime;
    }
}
