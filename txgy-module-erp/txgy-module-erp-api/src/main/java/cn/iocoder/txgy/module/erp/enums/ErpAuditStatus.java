package cn.iocoder.txgy.module.erp.enums;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * ERP 审核状态枚举
 *
 * @author OPENLAB BS
 */
@RequiredArgsConstructor
@Getter
public enum ErpAuditStatus implements ArrayValuable<Integer> {

    DRAFT(0, "草稿"),
    PROCESS(10, "审批中"),
    APPROVE(20, "审批通过"),
    REJECT(30, "审批驳回"),
    CANCEL(40, "已撤销");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ErpAuditStatus::getStatus).toArray(Integer[]::new);

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
