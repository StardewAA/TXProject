package cn.iocoder.txgy.framework.dict.config;

import cn.iocoder.txgy.framework.common.biz.system.dict.DictDataCommonApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 字典用到 Feign 的配置项
 *
 * @author OPENLAB BS
 */
@AutoConfiguration
@EnableFeignClients(clients = DictDataCommonApi.class) // 主要是引入相关的 API 服务
public class TxgyDictRpcAutoConfiguration {
}
