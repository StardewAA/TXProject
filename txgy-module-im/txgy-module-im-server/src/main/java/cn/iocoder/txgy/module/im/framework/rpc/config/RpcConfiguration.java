package cn.iocoder.txgy.module.im.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "imRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, WebSocketSenderApi.class})
public class RpcConfiguration {
}
