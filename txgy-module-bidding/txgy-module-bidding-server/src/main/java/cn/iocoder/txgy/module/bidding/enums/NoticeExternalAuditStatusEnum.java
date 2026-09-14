package cn.iocoder.txgy.module.bidding.enums;
// 规范依据：设计文档 v0.6 §7.2/§15.6（外部公告审核：审核转 ACTIVE 后触发 MQ 关注扇出+ES 索引事件）

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 外部公告审核状态（表 bidding_notice_external.audit_status）。
 *
 * <p>UNREVIEWED(采集入库) → APPROVED(审核通过转 ACTIVE 展示) / REJECTED(驳回)；
 * 上线后可由运营下线 TAKEN_DOWN（展示接口随即拒绝）。
 */
@Getter
@AllArgsConstructor
public enum NoticeExternalAuditStatusEnum {

    UNREVIEWED("UNREVIEWED", "待审核"),
    APPROVED("APPROVED", "已审核通过（转 ACTIVE）"),
    REJECTED("REJECTED", "已驳回（不展示）"),
    TAKEN_DOWN("TAKEN_DOWN", "已下线（曾上线后被撤下）");

    private final String status;
    private final String name;

    public static NoticeExternalAuditStatusEnum of(String status) {
        return Arrays.stream(values())
                .filter(item -> item.getStatus().equals(status))
                .findFirst()
                .orElse(null);
    }

}
