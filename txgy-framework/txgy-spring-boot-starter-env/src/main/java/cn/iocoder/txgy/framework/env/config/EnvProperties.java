package cn.iocoder.txgy.framework.env.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 环境配置
 *
 * @author OPENLAB BS
 */
@ConfigurationProperties(prefix = "txgy.env")
@Data
public class EnvProperties {

    public static final String TAG_KEY = "txgy.env.tag";

    /**
     * 环境标签
     */
    private String tag;

}
