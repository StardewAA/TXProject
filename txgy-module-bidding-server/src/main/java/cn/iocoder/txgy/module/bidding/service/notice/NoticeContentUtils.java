package cn.iocoder.txgy.module.bidding.service.notice;

/**
 * 公告正文版本判定工具：内容是否变化决定是否追加一个新版本（正文走版本化，见规范第五章第三节第 2 款）。
 * 口径：null 与空串等价；仅首尾空白差异不算实质变更（trim 后全等比较）。
 */
public final class NoticeContentUtils {

    private NoticeContentUtils() {
    }

    /**
     * 判断新旧正文是否需要追加新版本（trim 后比较；null/空串视为空）。
     *
     * @param existingContent 库内当前生效版本正文（无则 null）
     * @param newContent      拟写入的正文（无则 null/空白）
     * @return true=内容有实质变化，应追加新版本
     */
    public static boolean shouldAppendVersion(String existingContent, String newContent) {
        String existing = existingContent == null ? "" : existingContent.trim();
        String incoming = newContent == null ? "" : newContent.trim();
        return !existing.equals(incoming);
    }
}
