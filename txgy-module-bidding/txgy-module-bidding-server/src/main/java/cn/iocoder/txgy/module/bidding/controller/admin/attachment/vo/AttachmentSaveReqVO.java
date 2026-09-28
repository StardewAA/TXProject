package cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo;
// 规范依据：《招投标模块开发规范》§3.1（VO 规约：注释列来源与业务口径）；§4.4/§4.5（@Idempotent 幂等）
//   + 用户裁定 2026-09-24：针对主键的专门附件表（一对 N）；URL/对象存储承载。

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "bidding 附件保存 Request（上传走 POST /bidding/file/upload 拿到 fileId 后再绑定）")
@Data
public class AttachmentSaveReqVO {

    // 注：save 为纯绑定语义（幂等按 fileId 查重），无“按行 id 编辑”逻辑；删除走 DELETE ?id= 用附件行 id（RespVO.id），故请求体不含 id
    // 契约《投标与资格预审多文件化-2026-10-02》R1：新增 BID_TRACK（投标流程跟踪，bizCode=track.id）/
    // PREQUAL_APPLY（资格预审申请，bizCode=apply.id）；投标文件集/预审材料集经此绑定，主件 document_file_id 保留 CA 签章链路。
    @Schema(description = "归属实体：PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT/BID_TRACK/PREQUAL_APPLY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "归属实体类型不能为空")
    private String bizType;

    @Schema(description = "归属实体业务编号（project_code/tender_project_code/bid_section_code/notice_code/track.id/apply.id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "归属实体业务编号不能为空")
    private String bizCode;

    @Schema(description = "附件类型（35 类字典 AttachmentItemVO：TENDER_FILE/WIN_BID_NOTICE…）")
    private String attachmentType;

    @Schema(description = "附件集名称（AttachmentSetVO.ATTACHMENT_NAME，如\"招标文件\"）")
    private String attachmentName;

    @Schema(description = "FileApi 文件主键（上传接口返回值；一致性依据）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件 fileId 不能为空")
    private Long fileId;

    @Schema(description = "对象存储访问地址（上传接口返回值；SOCIAL 公开预览）")
    private String url;

    @Schema(description = "文件名（展示）")
    private String fileName;

    @Schema(description = "文件 SHA-256（红线 8 三件套；上传接口返回）")
    private String sha256;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "备注")
    private String remark;

}