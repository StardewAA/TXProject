package cn.iocoder.txgy.module.hrm.enums.safety;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 三级安全教育级别枚举
 *
 * <p>施工行业硬性合规项：公司级 → 项目级 → 班组级，三级全部合格才算「安全教育完成」。</p>
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmSafetyEducationLevelEnum implements ArrayValuable<Integer> {

    COMPANY(1, "公司级"),
    PROJECT(2, "项目级"),
    TEAM(3, "班组级");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmSafetyEducationLevelEnum::getLevel).toArray(Integer[]::new);

    private final Integer level;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static HrmSafetyEducationLevelEnum valueOf(Integer level) {
        return ArrayUtil.firstMatch(item -> item.getLevel().equals(level), values());
    }

}
