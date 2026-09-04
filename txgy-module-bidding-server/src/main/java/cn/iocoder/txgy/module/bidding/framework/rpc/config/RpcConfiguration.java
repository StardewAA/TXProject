package cn.iocoder.txgy.module.bidding.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * bidding 用到的跨模块接口，都在这里声明 Feign 客户端。
 *
 * <p>规范出处：docs/招投标模块开发规范.md §6 外部集成/跨模块调用（Feign 客户端集中声明）；
 * 证照文件走 FileApi（v3.3 §18.2 库内只存 file_id+sha256，取流经文件服务）。
 */
@Configuration(value = "biddingRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, DeptApi.class, FileApi.class})
public class RpcConfiguration {
}
