package cn.iocoder.txgy.module.bidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * bidding 模块（招投标业务）独立启动入口。
 *
 * <p>与 txgy 平台其它模块（hrm、fms 等）一致：仅声明启动类，业务按
 * docs/模块设计与规范/招投标模块开发规范.md 的分层组织在 controller/service/dal 等包中。
 */
@SpringBootApplication
public class BiddingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiddingServerApplication.class, args);
    }

}
