package cn.iocoder.txgy.module.wms.enums.md;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WmsItemQualityStatusEnum {
    PENDING(0, "待质检"),
    QUALIFIED(1, "合格"),
    ABNORMAL(2, "异常");

    private final Integer status;
    private final String name;

    public static boolean contains(Integer status) {
        return Arrays.stream(values()).anyMatch(item -> item.status.equals(status));
    }

    public static String nameOf(Integer status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status))
                .map(WmsItemQualityStatusEnum::getName).findFirst().orElse("待质检");
    }
}
