package cn.iocoder.txgy.module.hrm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 合同签署状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmContractSignStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待签"),
    SIGNED(1, "已签"),
    INVALID(2, "已作废"),
    ;

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return Arrays.stream(values()).map(HrmContractSignStatusEnum::getStatus).toArray(Integer[]::new);
    }

    public static HrmContractSignStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
