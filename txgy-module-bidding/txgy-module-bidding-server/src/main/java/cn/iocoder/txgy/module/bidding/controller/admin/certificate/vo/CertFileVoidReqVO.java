package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 证照文件版本「作废」请求 VO（F2：《后端变更通知-给前端.md》§8 —— 作废=软删留痕）。
 *
 * <p>规范出处：作废语义=「版本不再有效，但行保留展示（留痕）」；作废后该类别失去当前版本，
 * 用户可重新登记新版本（版本号继续递增）。作废原因必填——作废是整改依据，须留痕（与审查驳回同理）。
 */
public class CertFileVoidReqVO {

    /** 文件版本主键（file/list 返回的 id） */
    @NotNull(message = "文件版本不能为空")
    private Long fileVersionId;

    /** 作废原因（必填，留痕用） */
    @NotEmpty(message = "作废原因必填（作废是留痕操作，须写明原因）")
    private String reason;

    public Long getFileVersionId() { return fileVersionId; }
    public void setFileVersionId(Long fileVersionId) { this.fileVersionId = fileVersionId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
