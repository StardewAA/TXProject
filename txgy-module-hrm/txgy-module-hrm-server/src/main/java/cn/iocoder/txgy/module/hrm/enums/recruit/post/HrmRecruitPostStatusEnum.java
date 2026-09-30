package cn.iocoder.txgy.module.hrm.enums.recruit.post;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 招聘职位状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmRecruitPostStatusEnum implements ArrayValuable<Integer> {

    PAUSED(0, "已暂停"),
    PUBLISHED(1, "已发布"),
    CLOSED(2, "已关闭"),
    /** 草稿（Q-003 / I-007）：新建职位默认落草稿，不出现在招聘门户，确认无误后再「发布」 */
    DRAFT(3, "草稿");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(HrmRecruitPostStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static HrmRecruitPostStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
