package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "标书智能体 - 检索片段 VO")
public class BidAgentSegmentVO {

    @Schema(description = "片段分页入参（BidAgentSegmentPageReqVO）")
    @Data
    public static class PageReqVO extends PageParam {
        @Schema(description = "材料编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "材料编号不能为空")
        private Long materialId;
    }

    @Schema(description = "片段编辑入参（BidAgentSegmentEditReqVO）")
    @Data
    public static class EditReqVO {
        @Schema(description = "切片编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "切片编号不能为空")
        private Long id;
        @Schema(description = "切片正文", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "切片内容不能为空")
        private String content;
    }

    @Schema(description = "片段批量操作入参（BidAgentSegmentIdsReqVO）")
    @Data
    public static class IdsReqVO {
        @Schema(description = "切片编号列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "切片编号不能为空")
        private List<Long> ids;
    }

    @Schema(description = "片段出参（BidAgentSegmentRespVO）")
    @Data
    public static class RespVO {
        private Long id;
        private Long materialId;
        private Integer chunkNo;
        private String content;
        private String sourceBlockIds;
        private Integer pageNo;
        /** 是否被人工编辑过：0 否 1 是 */
        private Integer edited;
    }
}
