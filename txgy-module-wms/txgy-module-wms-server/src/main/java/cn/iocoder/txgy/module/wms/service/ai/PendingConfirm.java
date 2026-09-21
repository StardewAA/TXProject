package cn.iocoder.txgy.module.wms.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 删除待确认上下文：记录某用户在删除主数据时需要二次确认的信息。
 */
@Data
@AllArgsConstructor
public class PendingConfirm {

    /** 过期时间：5 分钟 */
    private static final long EXPIRE_MILLIS = 5 * 60 * 1000L;

    /** 主数据类型：item / category / brand / merchant / warehouse */
    private String domain;

    /** 目标对象名称 */
    private String targetName;

    /** 目标对象编号 */
    private Long targetId;

    /** 删除结果（含阻断原因与候选引导） */
    private WmsAiDeleteResult result;

    /** 创建时间戳 */
    private long createTime = System.currentTimeMillis();

    public static PendingConfirm of(String domain, WmsAiDeleteResult result) {
        return new PendingConfirm(domain, result.getTargetName(), result.getTargetId(), result,
                System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createTime > EXPIRE_MILLIS;
    }

}
