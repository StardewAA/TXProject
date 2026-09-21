package cn.iocoder.txgy.module.hrm.enums.certificate;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 证件预警通知状态枚举
 *
 * 预警记录是业务事实的可重建派生数据，通知失败不影响业务事实，仅记录失败原因并允许重试。
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmCertificateWarningNotifyStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待通知"),
    SUCCESS(1, "已通知"),
    FAILED(2, "通知失败");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmCertificateWarningNotifyStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static HrmCertificateWarningNotifyStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
