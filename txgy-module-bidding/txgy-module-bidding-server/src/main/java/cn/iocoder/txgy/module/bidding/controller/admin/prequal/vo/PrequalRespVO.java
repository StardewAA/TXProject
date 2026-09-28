package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 资格预审响应")
@Data
public class PrequalRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "资格预审编号（内部编号，前缀 P）")
    private String prequalCode;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "资格预审项目名称")
    private String title;

    @Schema(description = "关联资格预审公告编号")
    private String noticeCode;

    @Schema(description = "资格预审文件 FileApi 主键")
    private Long fileId;

    @Schema(description = "申请开始时间")
    private LocalDateTime applyStartTime;

    @Schema(description = "申请截止时间")
    private LocalDateTime applyDeadline;

    @Schema(description = "状态（PrequalStatusEnum）")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
