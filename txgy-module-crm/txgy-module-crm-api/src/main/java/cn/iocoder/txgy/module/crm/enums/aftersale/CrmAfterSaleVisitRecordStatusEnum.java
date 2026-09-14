package cn.iocoder.txgy.module.crm.enums.aftersale;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 售后回访记录状态
 *
 * <p>回访任务完成后会自动生成一条「待填写」的回访记录，用户填写内容后流转为「已填写」。
 *
 * @author txgycloud
 */
@RequiredArgsConstructor
@Getter
public enum CrmAfterSaleVisitRecordStatusEnum implements ArrayValuable<Integer> {

    TO_FILL(1, "待填写"),
    FILLED(2, "已填写");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CrmAfterSaleVisitRecordStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
