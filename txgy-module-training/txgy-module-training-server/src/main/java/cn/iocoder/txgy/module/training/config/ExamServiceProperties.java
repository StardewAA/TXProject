package cn.iocoder.txgy.module.training.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模块三「考试与认证」服务对接配置
 *
 * <p>课程管理（模块二）需要读取模块三的试卷列表用于「结课考试」下拉，
 * 并把课程配置的结课考试时间回传给模块三展示，通过 application.yaml 的
 * {@code training.exam.*} 配置（容器内默认指向 {@code http://training-exam:8000}）。</p>
 *
 * @author 云培训学院课题小组
 */
@Data
@Component
@ConfigurationProperties(prefix = "training.exam")
public class ExamServiceProperties {

    /**
     * 是否启用与模块三的联动（关闭时试卷下拉返回空列表，不影响课程保存）
     */
    private Boolean enabled = true;

    /**
     * 模块三服务基础地址（容器内为 http://training-exam:8000，本地直连为 http://localhost:8001）
     */
    private String baseUrl = "http://training-exam:8000";

    /**
     * 调用超时时间（毫秒）
     */
    private Integer timeoutMs = 10_000;

}
