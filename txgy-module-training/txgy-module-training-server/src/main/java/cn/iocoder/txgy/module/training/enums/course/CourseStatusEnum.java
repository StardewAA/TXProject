package cn.iocoder.txgy.module.training.enums.course;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 课程状态枚举
 *
 * 状态机：draft(草稿) →submit→ pending_review(待审核) →approve→ approved(待上架)
 *         →online→ on_shelf(已上架) →offline→ off_shelf(已下架)
 *         pending_review →reject(必填原因)→ draft
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum CourseStatusEnum {

    DRAFT(0, "草稿"),
    PENDING_REVIEW(1, "待审核"),
    APPROVED(2, "审核通过（待上架）"),
    ON_SHELF(3, "已上架"),
    OFF_SHELF(4, "已下架");

    private final Integer status;
    private final String name;

    public static CourseStatusEnum getByStatus(Integer status) {
        return Arrays.stream(values()).filter(item -> item.getStatus().equals(status)).findFirst().orElse(null);
    }

    public static boolean isValid(Integer status) {
        return getByStatus(status) != null;
    }

}
