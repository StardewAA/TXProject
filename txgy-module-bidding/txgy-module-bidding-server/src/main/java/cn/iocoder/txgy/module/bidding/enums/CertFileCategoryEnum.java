package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证照文件类别（cert_file_versions.file_category 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三（ORIGINAL 原件/COPY 副本/CHANGE_PAGE 变更页/
 * RENEWAL_PAGE 延续页/E_CERT 电子证照/OTHER）＋§18.1-2（自查“有原件/有电子证照”按 ORIGINAL/E_CERT 且 is_current=TRUE 判定）。
 * <p>设计遵循/针对：code 存储（《招投标模块开发规范》§5 三-7）；加注件（GB/T 36903 §5.4）以 COPY/E_CERT 行承载，
 * 其 copy_* 元数据四要素随文件登记填写。
 */
public enum CertFileCategoryEnum {

    /** 原件扫描件 */
    ORIGINAL,
    /** 副本 */
    COPY,
    /** 变更页 */
    CHANGE_PAGE,
    /** 延续页 */
    RENEWAL_PAGE,
    /** 电子证照（OFD/PDF，GB/T 36905） */
    E_CERT,
    /** 其他 */
    OTHER
}
