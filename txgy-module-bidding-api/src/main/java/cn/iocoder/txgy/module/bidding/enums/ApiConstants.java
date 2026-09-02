package cn.iocoder.txgy.module.bidding.enums;

import cn.iocoder.txgy.framework.common.enums.RpcConstants;

/**
 * bidding 模块 API 相关的常量。
 */
public class ApiConstants {

    /** 服务名，和 spring.application.name 保持一致。 */
    public static final String NAME = "bidding-server";

    /** RPC 接口的前缀。 */
    public static final String PREFIX = RpcConstants.RPC_API_PREFIX + "/bidding";

    public static final String VERSION = "1.0.0";

}
