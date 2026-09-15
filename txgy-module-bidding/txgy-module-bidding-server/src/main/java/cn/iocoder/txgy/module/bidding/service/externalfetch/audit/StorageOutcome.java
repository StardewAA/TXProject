package cn.iocoder.txgy.module.bidding.service.externalfetch.audit;

// 规范依据：用户口径 2026-09-10「严格校验…以及存储状态」；《招投标模块开发规范》红线 8（file_id + sha256 + size）。
// 业务目标：归档（存储）动作必须回报"到底存下来没有"——状态 + file_id + 原因，
//   供审计台账落库与排查（原实现只要能拿到 URL 就当作已归档，存储实际失败时无人知晓）。

import lombok.Getter;

/**
 * 归档（存储）结果：状态取 {@link CrawlDownloadAuditor#STORAGE_REGISTERED} /
 * {@link CrawlDownloadAuditor#STORAGE_FAILED}。
 */
@Getter
public class StorageOutcome {

    /** 存储状态：REGISTERED / FAILED */
    private final String status;
    /** 平台文件 id（红线 8 三件套之 file_id；未取到为 null） */
    private final Long fileId;
    /** 说明（成功=校验通过信息；失败=原因，直接进审计 message） */
    private final String detail;

    private StorageOutcome(String status, Long fileId, String detail) {
        this.status = status;
        this.fileId = fileId;
        this.detail = detail;
    }

    /** 归档成功且校验通过 */
    public static StorageOutcome registered(Long fileId, String detail) {
        return new StorageOutcome(CrawlDownloadAuditor.STORAGE_REGISTERED, fileId, detail);
    }

    /** 归档失败/校验不符（仍可能已拿到 URL，作为兜底展示地址） */
    public static StorageOutcome failed(Long fileId, String detail) {
        return new StorageOutcome(CrawlDownloadAuditor.STORAGE_FAILED, fileId, detail);
    }

    /** 未涉及归档（如纯 HTML 路径） */
    public static StorageOutcome none(String detail) {
        return new StorageOutcome(CrawlDownloadAuditor.STORAGE_NONE, null, detail);
    }

}
