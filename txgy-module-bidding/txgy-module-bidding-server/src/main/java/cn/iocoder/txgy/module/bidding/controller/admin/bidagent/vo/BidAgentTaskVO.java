package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 一键生成标书任务 VO（前台/后台共用） */
public class BidAgentTaskVO {

    @Schema(description = "一键生成入参")
    @Data
    public static class GenerateReqVO {
        @Schema(description = "项目编号（空=默认 0，用于无需选项目的一键生成）")
        private Long projectId;
        @Schema(description = "标段编号（空=默认 DEFAULT，用于无需选标段的一键生成）")
        private String bidSectionCode;
        @Schema(description = "招标文件材料编号（空=自动取该标段唯一的 TENDER 材料）")
        private Long tenderMaterialId;
        @Schema(description = "模板材料编号（空=跳过模板回填）")
        private Long templateMaterialId;
        @Schema(description = "标书类型：technical 技术标书 / business 商务标书（落台账并透传编排侧组织章节大纲）")
        private String docCategory;
        @Schema(description = "是否允许同标段重复发起（默认 false：已有进行中任务直接报 1_060_111_092）")
        private Boolean force;
        private String remark;
    }

    @Schema(description = "分页入参")
    @Data
    public static class PageReqVO extends PageParam {
        private Long projectId;
        private String status;
    }

    @Schema(description = "人工确认入参（HIL）")
    @Data
    public static class ConfirmReqVO {
        @Schema(description = "待确认问题的答复列表")
        @NotNull
        private java.util.List<Answer> answers;

        @Schema(description = "单个问题答复")
        @Data
        public static class Answer {
            @Schema(description = "问题编号（来自任务详情 pendingQuestions[].questionId）")
            @NotBlank
            private String questionId;
            @Schema(description = "答复内容（如：确认 / 修改意见 / 跳过）")
            @NotNull
            private String value;
        }
    }

    @Schema(description = "返回出参")
    @Data
    public static class RespVO {
        private Long id;
        private String taskNo;
        private Long projectId;
        private String bidSectionCode;
        @Schema(description = "标书类型：technical 技术标书 / business 商务标书")
        private String docCategory;
        private Long tenderMaterialId;
        private String tenderMaterialName;
        private Long templateMaterialId;
        private String status;
        private String currentStep;
        private Integer progress;
        private String agentTaskId;
        private String openQuestionsJson;
        private String stepDetailJson;
        private Long artifactFileId;
        private String artifactUrl;
        private String errorCode;
        private String errorMessage;
        private Integer totalTokens;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createTime;
        private String remark;
    }

    @Schema(description = "章节树/章节正文出参")
    @Data
    public static class OutlineRespVO {
        private Long taskId;
        private Long projectId;
        private String outlineJson;
        private java.util.List<Chapter> chapters;
    }

    @Schema(description = "章节")
    @Data
    public static class Chapter {
        private String nodeId;
        private String title;
        private String content;
        private String status;
        private Integer version;
    }
}
