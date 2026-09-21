package cn.iocoder.txgy.module.erp.service.recognition.provider;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用 HTTP OCR/结构化抽取适配器。
 *
 * <p>识别服务需返回文档编号、日期、供应商和明细项等统一字段；原始响应会完整留存。</p>
 */
@Component
@ConditionalOnProperty(name = "erp.recognition.provider", havingValue = "http")
public class HttpDocumentRecognitionProvider implements DocumentRecognitionProvider {

    @Resource
    private RestTemplate restTemplate;

    @Value("${erp.recognition.ocr-endpoint:}")
    private String endpoint;
    @Value("${erp.recognition.api-key:}")
    private String apiKey;

    @Override
    public String getType() {
        return "HTTP";
    }

    @Override
    public String recognize(String sourceFileUrl, String bizType) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("未配置 ERP OCR 服务，请在 application-local.yaml 设置 erp.recognition.ocr-endpoint");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(apiKey)) {
            headers.set("X-API-Key", apiKey);
        }
        Map<String, String> request = new HashMap<>();
        request.put("fileUrl", sourceFileUrl);
        request.put("bizType", bizType);
        String response = restTemplate.postForObject(endpoint,
                new HttpEntity<>(JsonUtils.toJsonString(request), headers), String.class);
        if (!StringUtils.hasText(response)) {
            throw new IllegalStateException("OCR 服务未返回识别结果");
        }
        return response;
    }

}
