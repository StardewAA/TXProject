package cn.iocoder.txgy.module.bidding.service.externalfetch.validate;

// 规范依据：《招投标模块开发规范》红线 8（文件三件套 file_id + sha256 + size）；
//   用户口径 2026-09-10「严格校验下载文件的完整性（如 MD5/SHA 校验）」。

import java.security.MessageDigest;

/**
 * 采集链路摘要工具（SHA-256 十六进制）。
 *
 * <p>业务目标：为"下载文件完整性校验"提供统一口径——与库内 sha256 列、FileApi 归档三件套一致，
 * 便于左侧（抓取时计算）与右侧（入库/审核时复算）比对，发现传输截断或内容被替换。
 */
public final class CrawlDigestUtils {

    private CrawlDigestUtils() {
    }

    /** SHA-256 十六进制（小写 64 位）；算法必然存在，异常时返回 null 由调用方判定为校验失败 */
    public static String sha256Hex(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

}
