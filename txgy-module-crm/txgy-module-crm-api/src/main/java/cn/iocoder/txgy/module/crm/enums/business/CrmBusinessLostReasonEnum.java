package cn.iocoder.txgy.module.crm.enums.business;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 商机丢单原因枚举
 *
 * <p>仅「输单」弹窗必填，商机表单中隐藏。
 * 对应字典 {@link cn.iocoder.txgy.module.crm.enums.DictTypeConstants#CRM_BUSINESS_LOST_REASON}
 *
 * @author 防腐保温项目组
 */
@RequiredArgsConstructor
@Getter
public enum CrmBusinessLostReasonEnum implements ArrayValuable<Integer> {

    BUDGET(1, "客户预算不足/预算取消"),
    COMPETITOR(2, "客户选择竞品"),
    SUSPENDED(3, "项目无限期搁置"),
    DECISION_MAKER_CHANGED(4, "客户内部决策人变更"),
    DEMAND_CHANGED(5, "客户需求重大变更"),
    OTHER(6, "其他"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmBusinessLostReasonEnum::getReason).toArray(Integer[]::new);

    /**
     * 原因值
     */
    private final Integer reason;
    /**
     * 原因名称
     */
    private final String name;

    public static CrmBusinessLostReasonEnum getByReason(Integer reason) {
        if (reason == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> ObjUtil.equal(item.getReason(), reason))
                .findFirst().orElse(null);
    }

    public static String getNameByReason(Integer reason) {
        CrmBusinessLostReasonEnum reasonEnum = getByReason(reason);
        return reasonEnum == null ? null : reasonEnum.getName();
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
