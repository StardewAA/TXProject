package cn.iocoder.txgy.module.fms.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "fmsRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, DeptApi.class, FileApi.class})
public class RpcConfiguration {
}
