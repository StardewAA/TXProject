package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 证件生命周期附件清单项（五页改造缺接口契约 Q16，2026-09-28）。
 *
 * <p>规范出处：Q16 契约——get 出参补 files[]（fileId/文件名/类别/上传时间）。
 * <p>为什么这样写：文件名/类别/上传时间取自证照文件版本台账（cert_file_versions，本地可解析）；
 * 佐证材料 evidenceFileIds 为 FileApi 文件主键，infra 未开放按 id 查元数据（仅按 URL），
 * 故该类条目 fileName 为空——前端可经既有 fileId 通道（上传回执缓存/URL 查询）补齐展示。
 */
@Schema(description = "管理后台 - 证件生命周期附件清单项")
@Data
public class CertLifecycleFileRespVO {

    @Schema(description = "文件主键（FileApi fileId，一致性依据）", example = "1001")
    private Long fileId;

    @Schema(description = "文件名（本地台账可解析时给出；佐证材料经 FileApi 上传的为空）", example = "营业执照.pdf")
    private String fileName;

    @Schema(description = "文件类别 code（ORIGINAL/COPY/CHANGE_PAGE/RENEWAL_PAGE/E_CERT/OTHER）", example = "ORIGINAL")
    private String fileCategory;

    @Schema(description = "上传时间", example = "1788668723000")
    private LocalDateTime uploadTime;
}
