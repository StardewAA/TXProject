package cn.iocoder.txgy.module.bidding.enums.flow;

import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;

/**
 * 公告生命周期状态（规范版，招标全流程标准模型）。
 *
 * <p>五态：DRAFT 草稿 → REVIEWING 审核中 → PUBLISHED 已发布 → WITHDRAWN 已撤回 / EXPIRED 已过期。
 * 测试模式（bidding.flow.mode=test）免审直发：DRAFT→PUBLISHED；实战模式走 REVIEWING 审核（政府/内部审批）。
 *
 * <p>与既有 {@link NoticeStatusEnum}（DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED，NoticeServiceImpl 在用）的映射：
 * DRAFT↔DRAFT、PUBLISHED↔PUBLISHED、WITHDRAWN↔UNPUBLISHED、EXPIRED≈ARCHIVED（留存语义）；
 * REVIEWING 为新增审核态（现有公告无审核环节）。服务层接入以本枚举为标准，存量读写保留旧枚举兼容。
 */
public enum AnnouncementStatusEnum {

    /** 草稿：编制中，可任意修改 */
    DRAFT("草稿", "编制中，可任意修改"),
    /** 审核中：提交内部或监管审核 */
    REVIEWING("审核中", "提交内部或监管审核"),
    /** 已发布：已对外公开发布 */
    PUBLISHED("已发布", "已对外公开发布"),
    /** 已撤回：发布后发现错误撤回（留痕） */
    WITHDRAWN("已撤回", "发布后发现错误撤回，留痕"),
    /** 已过期：公告有效期已过（如报名截止） */
    EXPIRED("已过期", "公告有效期已过");

    private final String label;
    private final String description;

    AnnouncementStatusEnum(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 既有公告状态（NoticeStatusEnum）→ 新状态机映射（供 NoticeServiceImpl 流转时同步维护 announcement_status）。
     */
    public static AnnouncementStatusEnum fromNotice(NoticeStatusEnum noticeStatus) {
        if (noticeStatus == null) {
            return DRAFT;
        }
        switch (noticeStatus) {
            case PUBLISHED:
                return PUBLISHED;
            case UNPUBLISHED:
                return WITHDRAWN;
            case ARCHIVED:
                return EXPIRED;
            default:
                return DRAFT;
        }
    }

    /**
     * 状态机流转校验：
     * DRAFT→REVIEWING（提交审核）/PUBLISHED（测试模式免审直发）；
     * REVIEWING→PUBLISHED（审核通过）/DRAFT（审核驳回退回）；
     * PUBLISHED→WITHDRAWN（撤回）/EXPIRED（到期）；WITHDRAWN→DRAFT（撤回后修改重发）。EXPIRED 终态。
     */
    public static boolean canTransit(AnnouncementStatusEnum from, AnnouncementStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        switch (from) {
            case DRAFT:
                return to == REVIEWING || to == PUBLISHED;
            case REVIEWING:
                return to == PUBLISHED || to == DRAFT;
            case PUBLISHED:
                return to == WITHDRAWN || to == EXPIRED;
            case WITHDRAWN:
                return to == DRAFT;
            default:
                // EXPIRED 终态
                return false;
        }
    }

    /** 是否终态（不可再流转） */
    public boolean isTerminal() {
        return this == EXPIRED;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static AnnouncementStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (AnnouncementStatusEnum status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
