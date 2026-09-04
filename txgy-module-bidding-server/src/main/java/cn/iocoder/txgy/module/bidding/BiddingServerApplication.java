package cn.iocoder.txgy.module.bidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * bidding 模块（招投标业务）独立启动入口。
 *
 * <p>规范出处：docs/招投标模块开发规范.md §2（模块独立启动类约定，与 hrm/fms 等模块一致）。
 * <p>设计遵循/针对：仅承载 @SpringBootApplication 与包扫描根，业务按规范分层落在
 * controller/service/dal 等包；组件扫描范围依赖模块包名 cn.iocoder.txgy.module.bidding。
 */
@SpringBootApplication
public class BiddingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiddingServerApplication.class, args);
    }

}
