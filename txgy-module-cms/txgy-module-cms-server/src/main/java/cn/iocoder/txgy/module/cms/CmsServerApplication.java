package cn.iocoder.txgy.module.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/** 法规 CMS 服务启动入口。业务实现和数据映射需后续补齐。 */
@SpringBootApplication
@EnableAsync
public class CmsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsServerApplication.class, args);
    }

}
