package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "学员端 - 课程媒资学习项 Response VO")
@Data
public class LearnerMediaItemVO {

    @Schema(description = "课程媒资 ID", example = "1")
    private Long mediaId;

    @Schema(description = "媒资类型：1 文档 2 视频 3 图片 4 VR", example = "2")
    private Integer mediaType;

    @Schema(description = "媒资类型名称", example = "视频")
    private String mediaTypeName;

    @Schema(description = "媒资名称", example = "实操演示")
    private String name;

    @Schema(description = "媒资地址", example = "https://media.local/video/xx.mp4")
    private String url;

    @Schema(description = "视频时长（秒）", example = "2700")
    private Integer durationSeconds;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "是否可学习（文档/视频），图片/VR 仅预览", example = "true")
    private Boolean learnable;

    @Schema(description = "累计有效学习秒数", example = "500")
    private Integer validSeconds;

    @Schema(description = "文档已覆盖页数", example = "3")
    private Integer docCoveredPages;

    @Schema(description = "文档总页数", example = "10")
    private Integer docTotalPages;

    @Schema(description = "完成比例（%），0-100", example = "30.00")
    private BigDecimal progressPercent;

    @Schema(description = "是否已完成", example = "false")
    private Boolean completed;

    @Schema(description = "本媒资分摊学分", example = "0.40")
    private BigDecimal creditShare;

    @Schema(description = "本媒资已获学分", example = "0.12")
    private BigDecimal creditEarned;

}
