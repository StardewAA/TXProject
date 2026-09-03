package cn.iocoder.txgy.module.bidding.enums;

/**
 * 公告/公示状态机（简化版先落四态；后续按业务需要扩展"已过期"等自动态）。
 *
 * <p>可流转表：DRAFT→PUBLISHED；PUBLISHED→UNPUBLISHED（撤下）；UNPUBLISHED→PUBLISHED（重发）；
 * DRAFT/PUBLISHED/UNPUBLISHED→ARCHIVED（归档）。其余一律拒绝。
 */
public enum NoticeStatusEnum {

    /** 草稿（仅本租户/本账号可见，大厅不可见） */
    DRAFT,
    /** 已发布（大厅可见的生效公告） */
    PUBLISHED,
    /** 已撤下（不再对外展示，保留留痕） */
    UNPUBLISHED,
    /** 已归档（只读留痕） */
    ARCHIVED;

    /**
     * 状态机流转校验。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true=允许流转
     */
    public static boolean canTransit(NoticeStatusEnum from, NoticeStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        switch (from) {
            case DRAFT:
                return to == PUBLISHED || to == ARCHIVED;
            case PUBLISHED:
                return to == UNPUBLISHED || to == ARCHIVED;
            case UNPUBLISHED:
                return to == PUBLISHED || to == ARCHIVED;
            default:
                // ARCHIVED 终态
                return false;
        }
    }
}
