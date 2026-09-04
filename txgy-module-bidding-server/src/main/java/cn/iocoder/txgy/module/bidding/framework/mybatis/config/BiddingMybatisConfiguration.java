package cn.iocoder.txgy.module.bidding.framework.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * bidding 侧 MyBatis-Plus 补充配置（乐观锁拦截器补注册）。
 *
 * <p>规范出处：《招投标模块开发规范》docs/招投标模块开发规范.md §4.5「并发与幂等」乐观锁款：
 * “并发覆盖热点列（version、状态、金额）在 DO 上加 MyBatis-Plus @Version，更新走 WHERE id=? AND version=?
 * ……框架未默认注册 OptimisticLockerInnerInterceptor，本模块须在自己的 MyBatis-Plus 配置里补注册后才生效，
 * 勿假设开箱可用；业务表 DDL 加 version int NOT NULL DEFAULT 0”；同节“平台实现核对（2026-09-03，
 * 源 txgy-framework）”确认：分页拦截器由平台 starter 注册，乐观锁拦截器在框架内未检索到注册点，需业务侧补。
 *
 * <p>设计遵循/针对：本模块不修改父项目（txgy-framework），故以 BeanPostProcessor 挂到平台已装配的
 * {@link MybatisPlusInterceptor} 实例上，在其初始化完成后追加 {@link OptimisticLockerInnerInterceptor}，
 * 使 DO 上的 {@code @Version} 乐观锁在运行时真正生效（幂等/互斥/乐观锁择用规则同 §4.5，见红线与择用款）；
 * 追加前先查重，避免与框架未来默认注册重复叠加。业务写路径（更新 status/version 等热点列）依赖本配置，
 * 对应表 DDL 需带 version 列（§4.5 乐观锁款、§5.5 检查项第 8 条）。
 */
@Configuration
public class BiddingMybatisConfiguration {

    /**
     * 乐观锁拦截器补注册：规范出处同上（§4.5 乐观锁款＋平台实现核对）。返回 static Bean，
     * 避免在 @Configuration 内提前实例化 MybatisPlusInterceptor 破坏平台装配顺序。
     */
    @Bean
    public static BeanPostProcessor optimisticLockerInterceptorPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MybatisPlusInterceptor interceptor) {
                    boolean hasOptimisticLocker = interceptor.getInterceptors().stream()
                            .anyMatch(inner -> inner instanceof OptimisticLockerInnerInterceptor);
                    if (!hasOptimisticLocker) {
                        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
                    }
                }
                return bean;
            }
        };
    }
}
