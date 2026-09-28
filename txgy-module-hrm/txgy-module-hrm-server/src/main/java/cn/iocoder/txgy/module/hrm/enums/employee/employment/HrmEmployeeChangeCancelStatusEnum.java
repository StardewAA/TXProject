package cn.iocoder.txgy.module.hrm.enums.employee.employment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HRM 员工异动撤销状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmEmployeeChangeCancelStatusEnum {

    NORMAL(0, "正常"),
    CANCELED(1, "已撤销");

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名字
     */
    private final String name;

}
