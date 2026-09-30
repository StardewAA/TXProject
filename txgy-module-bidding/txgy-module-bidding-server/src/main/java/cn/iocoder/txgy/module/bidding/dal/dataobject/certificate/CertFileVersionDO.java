package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 证照文件版本表（T 轨）。表：cert_file_versions（DDL 见 sql/history/2026-09-04-certificate.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三＋§18.2 v3.4 对齐；GB/T 36903—2018 §5.4 加注件元数据；
 * 《招投标模块开发规范》§5 三-5（文件经 FileApi，库内只记 file_id+sha256+size，URL 非一致性依据）。
 * <p>设计遵循/针对：① 上传人=BaseDO creator、时间=create_time（审计列不另建，开发规范 §5 四-1）；
 * ② 同类别版本只追加（uk_cert_cat_version），isCurrent 唯一 TRUE 由服务切换；③ 病毒扫描 CLEAN 才可预览/下载；
 * ④ 加注件（GB/T 36903 §5.4）四要素在此记录，与 GB/T 36906 §6.2 加注件下载入参对应。
 */
@TableName("cert_file_versions")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertFileVersionDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 关联证照（冗余父键 certificate_info.id） */
    private Long certId;
    /** 文件类别 code：ORIGINAL/COPY/CHANGE_PAGE/RENEWAL_PAGE/E_CERT/OTHER */
    private String fileCategory;
    /** 文件名 */
    private String fileName;
    /** 文件对象主键（FileApi） */
    private Long fileId;
    /** SHA-256（防篡改/去重） */
    private String fileHash;
    /** 文件格式：PDF/JPG/PNG/OFD（OFD=GB/T 36905 电子证照） */
    private String fileFormat;
    /** 字节数 */
    private Long fileSize;
    /** 加注件制作时间（GB/T 36903 5.4.1 JZJZZSJ） */
    private LocalDateTime copyCreationTime;
    /** 加注件制作者（GB/T 36903 5.4.2 JZJZZZ） */
    private String copyCreator;
    /** 加注件制作事由（GB/T 36903 5.4.3 JZJZZSY） */
    private String copyCause;
    /** 加注件有效期截止时间（GB/T 36903 5.4.4 JZJYXQJZSJ） */
    private LocalDateTime copyExpiringTime;
    /** 同类别文件版本号（只追加） */
    private Integer version;
    /** 当前生效版本（自查只取 TRUE 行） */
    private Boolean isCurrent;
    /** 病毒扫描状态 code：PENDING/UNKNOWN/CLEAN/INFECTED（统一口径字段名 virusStatus，见 BiddingFileVirusStatusEnum） */
    private String virusStatus;
    /** 备注 */
    private String remark;
    /**
     * 是否作废（F2：《后端变更通知-给前端.md》§8 —— 作废=软删留痕：行保留、标作废，
     * 不再作为有效版本；用户可重新登记新版本。作废行不参与自查/投标引用）。
     */
    private Boolean voided;
    /** 作废原因（用户填写；留痕用，作废行展示） */
    private String voidReason;
    /** 作废时间（留痕用；操作人见 BaseDO updater） */
    private LocalDateTime voidedAt;
}
