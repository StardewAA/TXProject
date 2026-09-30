package cn.iocoder.txgy.module.hrm.enums.certificate;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 证件预警处理状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmCertificateWarningStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待处理"),
    HANDLED(1, "已处理"),
    IGNORED(2, "已忽略");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmCertificateWarningStatusEnum::getStatus).toArray(Integer[]::new);

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmCertificateWarningStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
