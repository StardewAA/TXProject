package cn.iocoder.txgy.module.training.enums.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 培训班状态枚举
 *
 * 状态机：draft(草稿/未开班) →start(绑定课程后)→ running(进行中)
 *         running →finish→ finished(已结业)
 *         draft/running →cancel→ canceled(已取消)
 * 已开班后课程清单锁定；学员在 running 阶段仍可补录。
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum ClassStatusEnum {

    DRAFT(0, "草稿"),
    RUNNING(1, "进行中"),
    FINISHED(2, "已完成"),
    CANCELED(3, "已取消");

    private final Integer status;
    private final String name;

    public static ClassStatusEnum getByStatus(Integer status) {
        return Arrays.stream(values()).filter(item -> item.getStatus().equals(status)).findFirst().orElse(null);
    }

    public static boolean isValid(Integer status) {
        return getByStatus(status) != null;
    }

}
