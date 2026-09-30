package cn.iocoder.txgy.module.member.framework.rpc.config;

import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.logger.LoginLogApi;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.sms.SmsSendApi;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.SocialUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "memberRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {SmsCodeApi.class, SmsSendApi.class, LoginLogApi.class, SocialUserApi.class, SocialClientApi.class,
        cn.iocoder.txgy.module.product.api.enterprise.ProductEnterpriseCustomerApi.class,
        FileApi.class})
public class RpcConfiguration {
}
