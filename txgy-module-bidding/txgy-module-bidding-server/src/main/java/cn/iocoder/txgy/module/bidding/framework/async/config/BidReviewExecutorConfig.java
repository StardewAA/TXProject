package cn.iocoder.txgy.module.bidding.framework.async.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 智慧标书审查异步执行器。
 *
 * <p>方案依据：§2.5（P0 用有界线程池，不入 MQ）。拒绝策略为 CallerRunsPolicy——
 * 队列满时由提交线程同步跑，避免"任务已建但没人执行"的静默悬挂；
 * 真正的兜底（QUEUED 超时重投、RUNNING 超时回收）由 P1 的 BidReviewJob 负责。
 */
@Configuration
@EnableAsync
public class BidReviewExecutorConfig {

    public static final String EXECUTOR_NAME = "bidReviewExecutor";

    @Bean(EXECUTOR_NAME)
    public Executor bidReviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bid-review-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
