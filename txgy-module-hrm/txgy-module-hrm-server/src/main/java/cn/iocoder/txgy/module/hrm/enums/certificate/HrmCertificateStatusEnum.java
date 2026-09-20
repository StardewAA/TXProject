package cn.iocoder.txgy.module.hrm.enums.certificate;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 证件状态枚举
 *
 * 由每日定时任务根据证件有效期、复审日期与证件类型配置的提前期计算得出，不作为人工维护的字段。
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmCertificateStatusEnum implements ArrayValuable<Integer> {

    VALID(1, "有效"),
    EXPIRING(2, "即将到期"),
    EXPIRED(3, "过期"),
    CANCELLED(4, "注销");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmCertificateStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static HrmCertificateStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
