package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 上传文件 CA 认证检测 Response")
@Data
public class CaSealCheckRespVO {

    @Schema(description = "是否已有 CA 签章记录（签过章即 true；前端据此提示未认证文件）", example = "false")
    private Boolean caAuthed;

    @Schema(description = "最近签章记录ID（无记录为 null）")
    private Long sealId;

    @Schema(description = "签章会话 documentId")
    private String documentId;

    @Schema(description = "原文件名")
    private String documentName;

    @Schema(description = "签章单位")
    private String orgName;

    @Schema(description = "签章时间")
    private String sealTime;

    @Schema(description = "验签状态（UNVERIFIED 未验 / PASS 通过 / FAIL 不通过；无记录为 null）")
    private String verifyStatus;

    @Schema(description = "已签章文件 fileId（无记录为 null）")
    private Long sealFileId;

    @Schema(description = "已签章文件网络路径（PDF 带红色公章，可直接预览/下载）")
    private String sealFileUrl;

    @Schema(description = "已签章文件字节数（契约 A1：前端可直接填 documentSize；无记录为 null）")
    private Long sealFileSize;
}
