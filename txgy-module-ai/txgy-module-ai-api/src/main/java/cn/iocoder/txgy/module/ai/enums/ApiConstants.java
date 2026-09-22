package cn.iocoder.txgy.module.ai.enums;

import cn.iocoder.txgy.framework.common.enums.RpcConstants;

/**
 * AI 模块 API 相关的枚举
 */
public class ApiConstants {

    /**
     * 服务名
     *
     * 注意，需要保证和 spring.application.name 保持一致
     */
    public static final String NAME = "ai-server";

    public static final String PREFIX = RpcConstants.RPC_API_PREFIX + "/ai";

    public static final String VERSION = "1.0.0";

}
