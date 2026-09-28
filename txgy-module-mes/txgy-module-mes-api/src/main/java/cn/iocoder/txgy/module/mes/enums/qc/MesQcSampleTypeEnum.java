package cn.iocoder.txgy.module.mes.enums.qc;

import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 检验抽样方式枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesQcSampleTypeEnum implements ArrayValuable<Integer> {

    /**
     * 全检：基数全部检验
     */
    FULL(1, "全检"),
    /**
     * 固定数量：min(固定数量, 基数)
     */
    FIXED_QUANTITY(2, "固定数量"),
    /**
     * 按比例：基数 × 比例%，向上取整，且不超过基数
     */
    RATIO(3, "按比例");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MesQcSampleTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 抽样方式
     *
     * 字典 {@link DictTypeConstants#MES_QC_SAMPLE_TYPE}
     */
    private final Integer type;
    /**
     * 方式名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
