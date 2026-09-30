package cn.iocoder.txgy.module.infra.service.file;

import lombok.Data;

/**
 * OSS 提升补传结果（可追踪、可安全重试）
 *
 * <p>用于运维/定时任务重试「已确认公开但未进入公开 OSS」的文件后，返回本次处置统计。</p>
 *
 * @author OPENLAB BS
 */
@Data
public class OssPromoteRetryResult {

    /**
     * 本次扫描到的待补传文件总数
     */
    private int total;
    /**
     * 成功提升到 OSS 的文件数
     */
    private int success;
    /**
     * 仍失败（OSS 不可达等）的文件数
     */
    private int failed;
    /**
     * 已被跳过（已在 OSS 或参数非法）的文件数
     */
    private int skipped;
    /**
     * 附加说明（例如单文件重试的失败原因）
     */
    private String message;

    public static OssPromoteRetryResult of(int total, int success, int failed, int skipped) {
        OssPromoteRetryResult r = new OssPromoteRetryResult();
        r.total = total;
        r.success = success;
        r.failed = failed;
        r.skipped = skipped;
        return r;
    }

    public static OssPromoteRetryResult notFound() {
        OssPromoteRetryResult r = new OssPromoteRetryResult();
        r.message = "文件不存在";
        return r;
    }

    public static OssPromoteRetryResult notPublic() {
        OssPromoteRetryResult r = new OssPromoteRetryResult();
        r.message = "该文件不是已确认公开的文件，拒绝补传（私有文件不会迁移到公共桶）";
        return r;
    }

    public static OssPromoteRetryResult alreadyPromoted() {
        OssPromoteRetryResult r = new OssPromoteRetryResult();
        r.total = 1;
        r.skipped = 1;
        r.message = "该文件已成功在 OSS 中，无需补传";
        return r;
    }

    public static OssPromoteRetryResult singleSuccess() {
        OssPromoteRetryResult r = new OssPromoteRetryResult();
        r.total = 1;
        r.success = 1;
        return r;
    }

    public static OssPromoteRetryResult singleFailed(String reason) {
        OssPromoteRetryResult r = new OssPromoteRetryResult();
        r.total = 1;
        r.failed = 1;
        r.message = reason;
        return r;
    }
}
