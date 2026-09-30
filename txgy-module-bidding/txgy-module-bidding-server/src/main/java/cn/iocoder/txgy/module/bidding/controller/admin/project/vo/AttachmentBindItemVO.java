package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2 + 前端契约 29 §2.7（SaveReqVO 入参可选
//   [{ fileId, attachmentType?, attachmentName?, remark? }] —— 保存时一并绑定附件，幂等）
// [业务目标] 项目/招标项目/标段 Save 随主档一并绑定附件的轻量入参项；文件本体经
//   POST /bidding/file/upload 上传换取 fileId（一致性依据）后再随主档提交。
// [为什么] 不直接复用 AttachmentSaveReqVO：其 bizType/bizCode 由服务层按实体推导填充，
//           页面入参只需携带 fileId 与可选元数据，保持四实体 Save 入参扁平。

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "项目域实体附件绑定入参项（随 Save 一并提交，幂等）")
@Data
public class AttachmentBindItemVO {

    @Schema(description = "FileApi 文件主键（POST /bidding/file/upload 返回值；一致性依据）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "附件 fileId 不能为空")
    private Long fileId;

    @Schema(description = "附件类型（35 类字典 AttachmentItemVO：TENDER_FILE/WIN_BID_NOTICE…）")
    private String attachmentType;

    @Schema(description = "附件集名称（AttachmentSetVO.ATTACHMENT_NAME，如：招标文件）")
    private String attachmentName;

    @Schema(description = "备注")
    private String remark;

}
