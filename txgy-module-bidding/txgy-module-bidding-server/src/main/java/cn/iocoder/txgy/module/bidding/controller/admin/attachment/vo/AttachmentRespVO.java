package cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo;
// 规范依据：《招投标模块开发规范》§3.1（VO 规约）
//   + api/vo/AttachmentSetVO（附件集结构：ATTACHMENT_GUID/SET_CODE/NAME/TYPE/FILE_NAME/URL，URL=社交公开）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime; // 时间字段一律 LocalDateTime（规范 §三-3.1/§四-4.4-3；01 号契约口径：序列化为 epoch 毫秒，勿手写 Long）

@Schema(description = "bidding 附件 Response（一对 N 附件行）")
@Data
public class AttachmentRespVO {

    @Schema(description = "附件行主键")
    private Long id;
    @Schema(description = "归属实体：PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT")
    private String bizType;
    @Schema(description = "归属实体业务编号")
    private String bizCode;
    @Schema(description = "附件类型（35 类字典 AttachmentItemVO）")
    private String attachmentType;
    @Schema(description = "附件集名称（AttachmentSetVO.ATTACHMENT_NAME）")
    private String attachmentName;
    @Schema(description = "FileApi 文件主键（一致性依据）")
    private Long fileId;
    @Schema(description = "对象存储访问地址（SOCIAL 公开；预览/下载走 FileApi 签名 URL）")
    private String url;
    @Schema(description = "文件名")
    private String fileName;
    @Schema(description = "文件 SHA-256")
    private String sha256;
    @Schema(description = "文件大小（字节）")
    private Long size;
    @Schema(description = "MIME 类型")
    private String mimeType;
    @Schema(description = "病毒扫描状态：UNKNOWN/CLEAN/INFECTED（INFECTED 不下发下载 URL）")
    private String virusStatus;
    @Schema(description = "同实体内附件序号")
    private Integer seq;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间（服务端 LocalDateTime，序列化后为 epoch 毫秒 number，前端按 number 用）")
    private LocalDateTime createTime;

}