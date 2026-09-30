package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CA 签章记录 Response（签章证据链 + 验签状态）")
@Data
public class CaSealRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "签章会话 documentId")
    private String documentId;

    @Schema(description = "原文件名")
    private String documentName;

    @Schema(description = "原文件类型（pdf / other）")
    private String documentType;

    @Schema(description = "原文件 fileId")
    private Long fileId;

    @Schema(description = "原文件网络路径")
    private String fileUrl;

    @Schema(description = "原文件 SHA-256")
    private String fileSha256;

    @Schema(description = "文件 SM3 指纹（GB/T 32905，签名摘要）")
    private String sm3Hash;

    @Schema(description = "SM2 签名值（base64，真实可验）")
    private String signature;

    @Schema(description = "CA 证书序列号")
    private String certSn;

    @Schema(description = "签章单位名称")
    private String orgName;

    @Schema(description = "签章时间")
    private String sealTime;

    @Schema(description = "已签章文件 fileId（PDF=盖章 PDF；其他=签名信息 .sig.txt）")
    private Long sealFileId;

    @Schema(description = "已签章文件网络路径")
    private String sealFileUrl;

    @Schema(description = "已签章文件名")
    private String sealFileName;

    @Schema(description = "已签章文件字节数（契约 A1：供前端填 documentSize）")
    private Long sealFileSize;

    @Schema(description = "关联投标流程跟踪 id")
    private Long trackId;

    @Schema(description = "验签状态（UNVERIFIED 未验 / PASS 通过 / FAIL 不通过）")
    private String verifyStatus;

    @Schema(description = "验签时间")
    private LocalDateTime verifyTime;

    @Schema(description = "操作人用户ID")
    private Long operatorUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
