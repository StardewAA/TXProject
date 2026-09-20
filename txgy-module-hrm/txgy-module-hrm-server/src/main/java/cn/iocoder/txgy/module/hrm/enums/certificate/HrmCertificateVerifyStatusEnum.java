package cn.iocoder.txgy.module.hrm.enums.certificate;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 证照核验状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmCertificateVerifyStatusEnum implements ArrayValuable<Integer> {

    UNVERIFIED(0, "未核验"),
    VERIFIED(1, "已核验"),
    REJECTED(2, "核验不通过");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmCertificateVerifyStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmCertificateVerifyStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
