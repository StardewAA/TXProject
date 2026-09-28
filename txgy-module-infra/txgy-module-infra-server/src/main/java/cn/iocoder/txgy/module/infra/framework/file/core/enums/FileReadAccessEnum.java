package cn.iocoder.txgy.module.infra.framework.file.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件读取鉴权结果枚举
 *
 * @author OPENLAB BS
 */
@AllArgsConstructor
@Getter
public enum FileReadAccessEnum {

    /**
     * 允许读取（公开资源，或已通过归属/参与方校验的登录用户）
     */
    ALLOW(0, "允许"),
    /**
     * 需要登录（私有资源，但当前请求没有有效登录身份）
     */
    NEED_LOGIN(1, "需要登录"),
    /**
     * 拒绝访问（已登录，但不满足归属/参与方条件，或跨租户）
     */
    FORBIDDEN(2, "拒绝访问");

    /**
     * 结果码
     */
    private final Integer code;
    /**
     * 结果名
     */
    private final String name;

}
