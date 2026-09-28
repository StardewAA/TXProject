package cn.iocoder.txgy.module.bidding.enums.file;

/**
 * 病毒扫描状态（统一口径：《招投标模块开发规范》§5 三-5「上传须过病毒扫描与类型白名单」，
 * 参照外部公告附件参考实现 bidding_notice_external_attachment.virus_status）。
 *
 * <p>INFECTED 的文件一律拒绝下发（下载接口抛错），证照域原 `virusScanStatus` 统一收敛到本枚举。
 */
public enum BiddingFileVirusStatusEnum {

    /** 未知（尚未扫描；不拦截，但下载接口应记录） */
    UNKNOWN,
    /** 待扫描（证照域登记时默认态；语义同 UNKNOWN，保留以兼容既有数据） */
    PENDING,
    /** 安全（扫描通过） */
    CLEAN,
    /** 已感染（禁止预览/下载） */
    INFECTED;

    /**
     * 是否允许下发：仅 INFECTED 拒绝（CLEAN 通过；UNKNOWN/PENDING 为尚未扫描，不拦但接口应记录）。
     * 规范 §5 三-5 要求「上传须过病毒扫描」，扫描结果由扫描组件回写；未扫描不等于已感染。
     */
    public static boolean isDownloadable(String status) {
        return !INFECTED.name().equals(status);
    }
}
