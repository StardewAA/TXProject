package cn.iocoder.txgy.module.product.framework.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 仅注册旧 member 供应经营边界，不拦截采购中心的其他接口。 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class LegacyMemberSalesWebConfiguration implements WebMvcConfigurer {

    private final LegacyMemberSalesInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(
                "/product/supplier-product/**",
                "/product/supplier-shop/**",
                "/product/supplier-stock/**",
                "/product/purchase-inquiry/quote/**");
    }
}
