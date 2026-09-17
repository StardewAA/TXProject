package cn.iocoder.txgy.module.mes.service.wms.integration;

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
 * WMS 服务 HTTP 客户端（内部 RPC 通道）
 *
 * 调用 WMS 的 /rpc-api/** 内部接口（受信内部服务网络，携带租户上下文）。
 * 基地址与超时可配置；正式环境 WMS 部署于同一内网时使用容器网络地址。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Component
public class WmsServerHttpClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WmsServerHttpClient(
            @Value("${mes.wms.base-url:http://wms:48080}") String baseUrl,
            @Value("${mes.wms.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${mes.wms.read-timeout-ms:10000}") int readTimeoutMs) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * POST JSON 到 WMS 内部接口
     *
     * @param path       接口路径（如 /rpc-api/wms/integration/shipment/production）
     * @param tenantId   租户（来自任务行，写入 tenant-id 头）
     * @param body       请求体（将序列化为 JSON）
     * @return 响应体（CommonResult 结构：code/data/msg），网络异常时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Long tenantId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("tenant-id", String.valueOf(tenantId));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    /**
     * GET 查询 WMS 内部接口
     *
     * @param path     已含查询参数的完整路径
     * @param tenantId 租户
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path, Long tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("tenant-id", String.valueOf(tenantId));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (Map<String, Object>) resp.getBody();
    }

}
