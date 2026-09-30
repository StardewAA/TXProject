package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后回访任务状态
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleVisitTaskStatusEnum implements ArrayValuable<Integer> {

    PENDING(1, "待处理"),
    FINISH(2, "已完成"),
    OVERDUE(3, "已逾期"),
    /**
     * 已取消
     *
     * <p>使用场景：关联质保档案自动流转为「已到期终止」时，其下「计划回访日期晚于质保到期日」
     * 的未完成任务会被自动取消 —— 质保已终止，这些回访已无业务意义，
     * 取消后既不会继续出现在「任务提醒」待办中，也保留了完整痕迹。
     */
    CANCELED(4, "已取消");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleVisitTaskStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
