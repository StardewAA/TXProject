package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "学员端 - 文档学习状态 Response VO")
@Data
public class DocStatusRespVO {

    @Schema(description = "文档媒资 ID", example = "12")
    private Long mediaId;

    @Schema(description = "是否已完成（覆盖页数比例达标）", example = "false")
    private Boolean completed;

    @Schema(description = "累计有效停留秒数", example = "600")
    private Integer validSeconds;

    @Schema(description = "已覆盖页数", example = "6")
    private Integer coveredPages;

    @Schema(description = "文档总页数", example = "10")
    private Integer totalPages;

    @Schema(description = "学习进度（%）", example = "60.00")
    private BigDecimal progressPercent;

}
