package cn.iocoder.txgy.module.training.framework.rpc.config;

import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Feign RPC 客户端配置
 *
 * 模块三起，培训班督促（站内信）、学员姓名快照、上岗资格核查需要调用 system 服务
 *
 * @author 云培训学院课题小组
 */
@Configuration(value = "trainingRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {
        NotifyMessageSendApi.class, AdminUserApi.class, DeptApi.class
})
public class RpcConfiguration {
}
