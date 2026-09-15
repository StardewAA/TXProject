package cn.iocoder.txgy.module.cms.framework.rpc.config;

import cn.iocoder.txgy.module.system.api.logger.OperateLogApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * CMS 模块 RPC 客户端注册
 *
 * @author yuting_feng
 */
@Configuration(value = "cmsRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {OperateLogApi.class})
public class RpcConfiguration {
}
