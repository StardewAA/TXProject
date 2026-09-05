package cn.iocoder.txgy.module.bidding.enums;
// 规范依据：设计文档 v0.6 §7/§15.1；《招投标模块开发规范》§2.1（枚举登记习惯）

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 外部公告生命周期状态（表 bidding_notice_external.status）。
 *
 * <p>NEW → ACTIVE → INACTIVE → ARCHIVED；审核转 ACTIVE 后触发 ES 索引事件（MQ），
 * 列表/展示默认只放行 ACTIVE（public_scope=SOCIAL 见 NoticeExternalPublicScope）。
 */
@Getter
@AllArgsConstructor
public enum NoticeExternalStatusEnum {

    NEW("NEW", "待审核"),
    ACTIVE("ACTIVE", "生效中（可展示）"),
    INACTIVE("INACTIVE", "已失效（超生效期/源站下线）"),
    ARCHIVED("ARCHIVED", "已归档（只读留痕）");

    private final String status;
    private final String name;

    public static NoticeExternalStatusEnum of(String status) {
        return Arrays.stream(values())
                .filter(item -> item.getStatus().equals(status))
                .findFirst()
                .orElse(null);
    }

}
