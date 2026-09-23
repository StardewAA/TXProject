package cn.iocoder.txgy.module.ai.enums;

import cn.iocoder.txgy.framework.common.enums.RpcConstants;

/** AI module RPC constants. */
public final class ApiConstants {

    public static final String NAME = "ai-server";
    public static final String PREFIX = RpcConstants.RPC_API_PREFIX + "/ai";
    public static final String VERSION = "1.0.0";

    private ApiConstants() {
    }
}
