package cn.iocoder.txgy.module.infra.enums.file;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文件访问类型枚举
 *
 * 安全约定：**上传后默认一律为 {@link #PRIVATE}**，公开访问必须由服务端在业务落库时
 * （见 {@code FileApi#bindFile}）显式确认为 {@link #PUBLIC}，不接受客户端自行声明。
 *
 * 该枚举位于 infra-api，供业务模块（order / product / member 等）在调用
 * {@code FileApi#bindFile} 时显式声明目标访问类型，避免散落魔法值。
 *
 * @author OPENLAB BS
 */
@AllArgsConstructor
@Getter
public enum FileAccessTypeEnum implements ArrayValuable<Integer> {

    PUBLIC(1, "公开"), // 匿名可读（仅限服务端确认过的公开业务资源）
    PRIVATE(2, "私有"); // 必须登录，且通过归属/参与方校验后才可读

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(FileAccessTypeEnum::getAccessType).toArray(Integer[]::new);

    /**
     * 访问类型
     */
    private final Integer accessType;
    /**
     * 类型名
     */
    private final String name;

    public static FileAccessTypeEnum valueOf(Integer accessType) {
        return ArrayUtil.firstMatch(item -> item.getAccessType().equals(accessType), FileAccessTypeEnum.values());
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
