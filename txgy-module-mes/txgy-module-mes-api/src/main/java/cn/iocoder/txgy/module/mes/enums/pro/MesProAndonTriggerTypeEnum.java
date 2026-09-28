package cn.iocoder.txgy.module.mes.enums.pro;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 安灯触发类型枚举
 *
 * <p>用于区分安灯呼叫是「人工手动点亮」还是「系统基于业务事件自动触发」，
 * 自动触发类型同时作为「业务事件 → 安灯配置」的匹配键（见 {@link MesProAndonConfigDO#getTriggerType()}）。</p>
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesProAndonTriggerTypeEnum implements ArrayValuable<Integer> {

    MANUAL(0, "人工呼叫"),
    IPQC_FAIL(1, "质检不合格"),
    SHORTAGE(2, "物料缺料"),
    WO_OVERDUE(3, "工单超期");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesProAndonTriggerTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 类型名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
