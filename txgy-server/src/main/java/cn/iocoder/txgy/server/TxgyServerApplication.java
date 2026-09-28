package cn.iocoder.txgy.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 项目的启动类
 *
 * 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
 * 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
 * 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
 *
 * @author OPENLAB BS
 */
@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${txgy.info.base-package}
@SpringBootApplication(excludeName = {
        // RPC 相关
//        "org.springframework.cloud.openfeign.FeignAutoConfiguration",
//        "cn.iocoder.txgy.module.system.framework.rpc.config.RpcConfiguration"
})
// 单体（boot）模式下，各模块的 API 都由本进程内的 *ApiImpl 提供实现（@Primary）。
// 模块自带的 RpcConfiguration 会通过 @EnableFeignClients 再注册一份同类型的 @FeignClient 代理（也是 primary），
// 造成「同类型多个 primary Bean」冲突（如 PermissionApi、AdminUserApi），故扫描时排除模块的 RpcConfiguration。
// 微服务形态不受影响：各模块仍由自己的 *ServerApplication 扫描并注册 Feign 客户端。
@ComponentScan(basePackages = {"${txgy.info.base-package}.server", "${txgy.info.base-package}.module"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "cn\\.iocoder\\.txgy\\.module\\..*\\.framework\\.rpc\\.config\\.RpcConfiguration"))
public class TxgyServerApplication {

    public static void main(String[] args) {
        // 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章

        SpringApplication.run(TxgyServerApplication.class, args);
//        new SpringApplicationBuilder(TxgyServerApplication.class)
//                .applicationStartup(new BufferingApplicationStartup(20480))
//                .run(args);

        // 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.iocoder.cn/quick-start/ 文章
    }

}
