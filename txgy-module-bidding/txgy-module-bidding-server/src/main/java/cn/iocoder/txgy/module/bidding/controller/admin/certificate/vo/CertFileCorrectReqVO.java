package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 证照文件版本「更正元数据」请求 VO（F2：《后端变更通知-给前端.md》§8 —— 更正备注/加注件元数据）。
 *
 * <p>设计遵循/针对：只允许更正**展示/留痕类**元数据（remark + 加注件 copy_* 四要素），
 * 不允许改 certId/fileCategory/fileHash/fileId（这些是版本链的不变约束，改了会破坏判重与版本语义）；
 * 至少传一个可更正字段，否则抛 CERT_FILE_CORRECT_NO_FIELD。
 */
public class CertFileCorrectReqVO {

    /** 文件版本主键（file/list 返回的 id） */
    @NotNull(message = "文件版本不能为空")
    private Long fileVersionId;

    /** 备注（可空：不传则不改） */
    private String remark;

    /** 加注件制作时间（GB/T 36903 5.4.1 JZJZZSJ；可空：不传则不改） */
    private LocalDateTime copyCreationTime;

    /** 加注件制作者（GB/T 36903 5.4.2 JZJZZZ） */
    private String copyCreator;

    /** 加注件制作事由（GB/T 36903 5.4.3 JZJZZSY） */
    private String copyCause;

    /** 加注件有效期截止时间（GB/T 36903 5.4.4 JZJYXQJZSJ） */
    private LocalDateTime copyExpiringTime;

    public Long getFileVersionId() { return fileVersionId; }
    public void setFileVersionId(Long fileVersionId) { this.fileVersionId = fileVersionId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCopyCreationTime() { return copyCreationTime; }
    public void setCopyCreationTime(LocalDateTime copyCreationTime) { this.copyCreationTime = copyCreationTime; }
    public String getCopyCreator() { return copyCreator; }
    public void setCopyCreator(String copyCreator) { this.copyCreator = copyCreator; }
    public String getCopyCause() { return copyCause; }
    public void setCopyCause(String copyCause) { this.copyCause = copyCause; }
    public LocalDateTime getCopyExpiringTime() { return copyExpiringTime; }
    public void setCopyExpiringTime(LocalDateTime copyExpiringTime) { this.copyExpiringTime = copyExpiringTime; }
}
