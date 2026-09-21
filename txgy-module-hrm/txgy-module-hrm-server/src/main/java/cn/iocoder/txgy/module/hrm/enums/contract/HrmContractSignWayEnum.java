package cn.iocoder.txgy.module.hrm.enums.contract;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 合同签署方式枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmContractSignWayEnum implements ArrayValuable<Integer> {

    OFFLINE(1, "线下签署"),
    ELECTRONIC(2, "电子签章"),
    ;

    private final Integer way;
    private final String name;

    @Override
    public Integer[] array() {
        return Arrays.stream(values()).map(HrmContractSignWayEnum::getWay).toArray(Integer[]::new);
    }

}
