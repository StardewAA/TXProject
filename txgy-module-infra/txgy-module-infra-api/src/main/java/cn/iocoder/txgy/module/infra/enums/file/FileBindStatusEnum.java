package cn.iocoder.txgy.module.infra.enums.file;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文件业务绑定状态枚举
 *
 * 上传时先落为 {@link #UNBOUND}（待绑定），只有上传者本人在短窗口内可读；
 * 业务单据提交成功后（订单 / 询价 / 店铺 / 商品等），由服务端核验归属并落为 {@link #BOUND}，
 * 之后按单据与参与方权限判定读取。
 *
 * 该枚举位于 infra-api，供业务模块在调用 {@code FileApi#bindFile} 时复用，避免散落魔法值。
 *
 * @author OPENLAB BS
 */
@AllArgsConstructor
@Getter
public enum FileBindStatusEnum implements ArrayValuable<Integer> {

    UNBOUND(0, "待绑定"), // 尚未绑定任何业务单据：仅上传者短时可读
    BOUND(1, "已绑定"), // 已绑定业务单据：按单据归属与参与方判定
    PUBLIC(2, "公开资源"); // 服务端确认的公开资源：匿名可读

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(FileBindStatusEnum::getStatus).toArray(Integer[]::new);

    /**
     * 绑定状态
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    public static FileBindStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), FileBindStatusEnum.values());
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
