package cn.iocoder.txgy.module.bidding.framework.mybatis.config;
// 规范依据：《招投标模块开发规范》§4.5（@Version 乐观锁运行时生效）

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * bidding 侧 MyBatis-Plus 补充配置。
 *
 * <p>平台 starter（txgy-spring-boot-starter-mybatis）只注册了分页拦截器，
 * 未注册乐观锁拦截器；本模块不能改父项目，故用 BeanPostProcessor 在
 * {@link MybatisPlusInterceptor} 实例化后追加 {@link OptimisticLockerInnerInterceptor}，
 * 使 {@code @Version} 乐观锁（规范 4.5：状态可更新表带 version）在运行时真正生效。
 */
@Configuration
public class BiddingMybatisConfiguration {

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
