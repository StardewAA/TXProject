package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 技能 VO（轻量提示词增强包，可自动挂载到适用智能体） */
public class BidAgentSkillVO {

    @Schema(description = "分页入参")
    @Data
    public static class PageReqVO extends PageParam {
        private String code;
        private String name;
        private Integer status;
    }

    @Schema(description = "保存入参")
    @Data
    public static class SaveReqVO {
        private Long id;
        @NotBlank(message = "技能编码不能为空")
        private String code;
        @NotBlank(message = "技能名称不能为空")
        private String name;
        private String description;
        private String promptFragment;
        private String knowledgeRoles;
        private String toolWhitelist;
        @Schema(description = "适用任务类型：PARSE/OUTLINE/DRAFT/FILL（可多个，逗号分隔）")
        private String applicableTaskTypes;
        private Integer autoAttach;
        private Integer status;
        private Integer sort;
        private String remark;
    }

    @Schema(description = "返回出参")
    @Data
    public static class RespVO {
        private Long id;
        /** 租户编号：0=平台预置（管理页可见但不可改），>0=租户自建（可改） */
        private Long tenantId;
        private String code;
        private String name;
        private String description;
        private String promptFragment;
        private String knowledgeRoles;
        private String toolWhitelist;
        private String applicableTaskTypes;
        private Integer autoAttach;
        private Integer status;
        private Integer sort;
        private String remark;
    }

    @Schema(description = "智能体技能关联入参")
    @Data
    public static class ProfileSkillReqVO {
        @NotNull(message = "智能体配置编号不能为空")
        private Long profileId;
        private List<Long> skillIds;
    }
}
