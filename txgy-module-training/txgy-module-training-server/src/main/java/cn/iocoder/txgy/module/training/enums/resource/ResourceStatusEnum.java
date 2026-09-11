package cn.iocoder.txgy.module.training.enums.resource;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文档/视频资源状态枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceStatusEnum {

    DRAFT(1, "草稿"),
    ONLINE(2, "上线"),
    OFFLINE(3, "下架"),
    ARCHIVED(4, "归档");

    private final Integer status;
    private final String name;

    public static ResourceStatusEnum fromStatus(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

    public static boolean isValid(Integer status) {
        return Arrays.stream(values()).anyMatch(item -> item.getStatus().equals(status));
    }
}
