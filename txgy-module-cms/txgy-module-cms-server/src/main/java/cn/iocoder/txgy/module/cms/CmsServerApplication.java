package cn.iocoder.txgy.module.cms;

import cn.iocoder.txgy.module.ai.api.model.AiRuntimeModelApi;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.im.api.group.ImGroupApi;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.refund.PayRefundApi;
import cn.iocoder.txgy.module.system.api.permission.PermissionApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 法规 CMS 服务启动入口。业务实现和数据映射需后续补齐。 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableFeignClients(clients = {AiRuntimeModelApi.class, FileApi.class, ImGroupApi.class, PayOrderApi.class,
        PayRefundApi.class, PermissionApi.class})
public class CmsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsServerApplication.class, args);
    }

}
