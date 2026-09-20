package cn.iocoder.txgy.module.hrm.enums.certificate;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 证件续证/复审类型枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmCertificateRenewalTypeEnum implements ArrayValuable<Integer> {

    RENEW(1, "续证"),
    REVIEW(2, "复审");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmCertificateRenewalTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 名字
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmCertificateRenewalTypeEnum valueOf(Integer type) {
        return ArrayUtil.firstMatch(item -> item.getType().equals(type), values());
    }

}
