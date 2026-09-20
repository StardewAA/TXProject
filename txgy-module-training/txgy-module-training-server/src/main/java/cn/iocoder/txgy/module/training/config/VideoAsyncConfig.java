package cn.iocoder.txgy.module.training.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 视频异步处理线程池配置（上传归位 + 多清晰度转码）
 *
 * @author 云培训学院课题小组
 */
@Configuration(proxyBeanMethods = false)
public class VideoAsyncConfig {

    @Resource
    private VideoTranscodeProperties transcodeProperties;

    @Bean("videoAsyncExecutor")
    public Executor videoAsyncExecutor() {
        int poolSize = transcodeProperties.getPoolSize() == null || transcodeProperties.getPoolSize() < 1
                ? 1 : transcodeProperties.getPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(Math.max(poolSize, 2));
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("video-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(VideoAsyncConfig::decorate);
        executor.initialize();
        return executor;
    }

    /**
     * 传递请求上下文（RequestContextHolder）到异步线程，避免子线程中鉴权信息丢失
     */
    private static Runnable decorate(Runnable runnable) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            try {
                RequestContextHolder.setRequestAttributes(attributes);
                runnable.run();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}
