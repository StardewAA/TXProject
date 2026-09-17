package cn.iocoder.txgy.module.mes.service.erp.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * ERP 服务 HTTP 客户端（内部 RPC 通道）
 *
 * 调用 ERP 的 /rpc-api/** 内部接口（受信内部服务网络，携带租户上下文）。
 * 基地址与超时可配置，与 WmsServerHttpClient 同构。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Component
public class ErpServerHttpClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ErpServerHttpClient(
            @Value("${mes.erp.base-url:http://erp:48080}") String baseUrl,
            @Value("${mes.erp.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${mes.erp.read-timeout-ms:10000}") int readTimeoutMs) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * PUT JSON 到 ERP 内部接口
     *
     * @param path     接口路径（如 /rpc-api/erp/production-progress/report）
     * @param tenantId 租户（写入 tenant-id 头）
     * @param body     请求体
     * @return 响应体（CommonResult 结构），网络异常时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> put(String path, Long tenantId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("tenant-id", String.valueOf(tenantId));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + path, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
        return (Map<String, Object>) resp.getBody();
    }

}
