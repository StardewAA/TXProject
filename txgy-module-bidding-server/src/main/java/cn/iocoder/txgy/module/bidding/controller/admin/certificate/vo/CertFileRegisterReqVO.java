package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 证照文件登记 自建 VO（页面/内部接口；文件本体经 FileApi 上传，本 VO 只登记元数据 ——
 * 开发规范 §5 三-5：业务表只记 file_id/sha256/size）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三（file_category/版本/病毒扫描口径）＋§18.2；
 * GB/T 36903—2018 §5.4（加注件 copy_* 四要素，仅加注件行填写）。
 * <p>设计遵循/针对：① 同证同哈希重复登记抛 CERT_FILE_HASH_DUPLICATE（UK uk_cert_file_hash）；
 * ② 版本号与 is_current 由服务计算（同类别版本只追加，v3.3 §三）；③ 病毒扫描状态默认 PENDING，
 * 由扫描组件 markScanResult 回写 CLEAN/INFECTED（CLEAN 才可预览/下载）。
 */
public class CertFileRegisterReqVO {

    /** 关联证照 ID（必填） */
    @NotNull(message = "关联证照不能为空")
    private Long certId;
    /** 文件类别 code（缺省 ORIGINAL）：ORIGINAL/COPY/CHANGE_PAGE/RENEWAL_PAGE/E_CERT/OTHER */
    private String fileCategory;
    /** 文件名 */
    private String fileName;
    /** 文件对象主键（FileApi，必填） */
    @NotNull(message = "文件对象不能为空")
    private Long fileId;
    /** SHA-256（必填，防篡改/去重） */
    @NotNull(message = "文件哈希不能为空")
    private String fileHash;
    /** 文件格式：PDF/JPG/PNG/OFD（OFD=GB/T 36905 电子证照） */
    private String fileFormat;
    /** 字节数 */
    private Long fileSize;
    /** 加注件制作时间（GB/T 36903 5.4.1 JZJZZSJ；仅加注件行） */
    private LocalDateTime copyCreationTime;
    /** 加注件制作者（GB/T 36903 5.4.2 JZJZZZ） */
    private String copyCreator;
    /** 加注件制作事由（GB/T 36903 5.4.3 JZJZZSY） */
    private String copyCause;
    /** 加注件有效期截止时间（GB/T 36903 5.4.4 JZJYXQJZSJ） */
    private LocalDateTime copyExpiringTime;
    /** 备注 */
    private String remark;

    public Long getCertId() { return certId; }
    public void setCertId(Long certId) { this.certId = certId; }
    public String getFileCategory() { return fileCategory; }
    public void setFileCategory(String fileCategory) { this.fileCategory = fileCategory; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public LocalDateTime getCopyCreationTime() { return copyCreationTime; }
    public void setCopyCreationTime(LocalDateTime copyCreationTime) { this.copyCreationTime = copyCreationTime; }
    public String getCopyCreator() { return copyCreator; }
    public void setCopyCreator(String copyCreator) { this.copyCreator = copyCreator; }
    public String getCopyCause() { return copyCause; }
    public void setCopyCause(String copyCause) { this.copyCause = copyCause; }
    public LocalDateTime getCopyExpiringTime() { return copyExpiringTime; }
    public void setCopyExpiringTime(LocalDateTime copyExpiringTime) { this.copyExpiringTime = copyExpiringTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
