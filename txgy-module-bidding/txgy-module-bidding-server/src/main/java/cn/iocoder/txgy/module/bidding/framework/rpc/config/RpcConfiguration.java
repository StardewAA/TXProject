package cn.iocoder.txgy.module.bidding.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * bidding 用到的跨模块接口，都在这里声明 Feign 客户端。
 */
@Configuration(value = "biddingRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, DeptApi.class, FileApi.class})
public class RpcConfiguration {
}
