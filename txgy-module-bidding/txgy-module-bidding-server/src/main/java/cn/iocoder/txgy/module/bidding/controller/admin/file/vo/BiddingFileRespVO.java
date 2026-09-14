package cn.iocoder.txgy.module.bidding.controller.admin.file.vo;

// [规范依据]《招投标模块开发规范》§5 三-5（文件经 FileApi，库内只记 file_id + sha256 + size，
//   URL 非一致性依据）+ 红线 8（附件/版式文件三件套）；§4.7 段位表 101=公告与文件。
// [业务目标] 让前端「一次上传即拿到 file_id」：业务表（notice.pdf_file_id、notice_external_attachment.file_id 等）
//   以 file_id 关联文件，前端表单必须能直接绑定它，无需用户手填、也无需二次请求。
// [为什么] 出参同时给 fileId 与 url：fileId 是落库关联键（权威），url 仅用于上传后即时预览；
//   两者都返回可避免前端为预览再去调 /infra/file/get（该接口需 infra:file:query 权限，业务账号通常没有）。

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - bidding 文件上传 Response VO")
@Data
public class BiddingFileRespVO {

    @Schema(description = "文件编号（全局唯一，infra_file.id；业务表落库关联键）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long fileId;

    @Schema(description = "文件访问地址（平台文件服务 URL，用于上传后即时预览）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/attachment/xxx.pdf")
    private String url;

    @Schema(description = "原始文件名", example = "招标公告.pdf")
    private String name;

    @Schema(description = "文件 MIME 类型", example = "application/pdf")
    private String type;

    @Schema(description = "文件大小（字节）", example = "204800")
    private Long size;

    @Schema(description = "文件 SHA-256（防篡改/去重；红线 8 三件套之一。按 URL 补登记时无法计算，返回 null）",
            example = "9f2c...")
    private String sha256;

}
