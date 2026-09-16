package cn.iocoder.txgy.module.crm.enums.product;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * CRM 产品数据来源
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum CrmProductDataSourceEnum implements ArrayValuable<Integer> {

    MANUAL(1, "手工创建"),
    WMS_SYNC(2, "WMS 同步");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmProductDataSourceEnum::getDataSource).toArray(Integer[]::new);

    /**
     * 数据来源
     */
    private final Integer dataSource;
    /**
     * 来源名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isWmsSync(Integer dataSource) {
        return ObjUtil.equal(WMS_SYNC.dataSource, dataSource);
    }

}
