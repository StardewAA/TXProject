package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证照文件病毒扫描状态（cert_file_versions.virus_scan_status 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三（virus_scan_status，上传须扫描通过 CLEAN 才可预览/下载，
 * 高安全底线）；《招投标模块开发规范》§5 三-5（上传须过病毒扫描与类型白名单）。
 * <p>设计遵循/针对：登记时默认 PENDING，扫描组件回写 CLEAN/INFECTED（markScanResult）；INFECTED 行自动置
 * is_current=0 禁止作为当前版本使用（v3.3 §三口径）；code 存储（开发规范 §5 三-7）。
 */
/**
 * @deprecated 2026-09-20 统一到 {@link cn.iocoder.txgy.module.bidding.enums.file.BiddingFileVirusStatusEnum}
 * （字段名统一 `virusStatus`，值域 UNKNOWN/PENDING/CLEAN/INFECTED；规范 §5 三-5 与外部公告附件参考实现同口径）。
 * 本枚举保留仅为兼容，请勿在新代码中引用。
 */
@Deprecated
public enum CertificateVirusScanStatusEnum {

    /** 待扫描 */
    PENDING,
    /** 扫描通过（可预览/下载） */
    CLEAN,
    /** 发现病毒（不可用） */
    INFECTED
}
