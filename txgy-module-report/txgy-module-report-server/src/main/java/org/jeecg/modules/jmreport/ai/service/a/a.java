package org.jeecg.modules.jmreport.ai.service.a;

import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.jeecg.modules.jmreport.ai.conf.JimuAiConfigBean;
import org.jeecg.modules.jmreport.ai.service.IJimuAiAssistantService;
import org.jeecg.modules.jmreport.common.expetion.JimuReportException;
import org.jeecg.modules.jmreport.common.util.JimuSpringContextUtils;
import org.jeecg.modules.jmreport.common.util.OkConvertUtils;
import org.jeecg.modules.jmreport.config.client.JmReportTokenClient;
import org.jeecg.modules.jmreport.desreport.util.JmReportUtil;
import org.jeecg.modules.jmreport.dyndb.util.LocalCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JimuReport AI 助手服务实现（Shadow Patch）。
 * <p>
 * 说明：jimureport-spring-boot3-starter 2.3.4 的原始实现（混淆类）在静态初始化时调用
 * {@code HttpComponentsClientHttpRequestFactory#setConnectTimeout(int)}，该方法在
 * Spring Framework 7 中已被移除，导致 NoSuchMethodError 启动失败。
 * 本类与原类保持相同 FQCN（org.jeecg.modules.jmreport.ai.service.a.a），
 * 打包后优先于 jar 内的原类被加载；连接超时改由 Apache HttpClient 5 的 RequestConfig 配置。
 *
 * @author OPENLAB BS
 */
@Service("jimuAiAssistantServiceImpl")
public class a implements IJimuAiAssistantService {

    private static final Logger a = LoggerFactory.getLogger(a.class);
    private static final Object c = new Object();
    private static final RestTemplate f;
    private static JmReportTokenClient e;

    @Autowired
    JimuAiConfigBean jimuAiConfigBean;

    @Autowired
    LocalCache localCache;

    static {
        CloseableHttpClient httpClient = getHttpsClient();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        factory.setConnectionRequestTimeout(60000);
        factory.setReadTimeout(60000);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().add(new FormHttpMessageConverter());
        restTemplate.getMessageConverters().set(1,
                new StringHttpMessageConverter(StandardCharsets.UTF_8));
        f = restTemplate;
    }

    public static CloseableHttpClient getHttpsClient() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContexts.custom()
                    .loadTrustMaterial((KeyStore) null, new TrustStrategy() {
                        @Override
                        public boolean isTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            return true;
                        }
                    })
                    .build();
        } catch (java.security.GeneralSecurityException ex) {
            ex.getStackTrace();
        }
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory((LayeredConnectionSocketFactory) socketFactory)
                        .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(60000, TimeUnit.MILLISECONDS)
                        .build())
                .build();
    }

    @Override
    public JSONObject callAiService(String content, String ddl, String dbType, String bizType) {
        Integer maxTokens = jimuAiConfigBean.getMaxTokens();
        if (maxTokens != null && ddl != null && ddl.length() > maxTokens - 1000) {
            a.warn("[AI] ddl长度({})超过maxTokens-1000({})已截断", ddl.length(), maxTokens - 1000);
            ddl = ddl.substring(0, maxTokens - 1000);
        }
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("content", content);
        inputParams.put("ddl", ddl);
        inputParams.put("dbtype", b(dbType));
        inputParams.put("bizType", bizType);
        Map<String, Object> params = new HashMap<>();
        params.put("flowId", "1909856345692065793");
        params.put("inputParams", inputParams);
        params.put("responseMode", "blocking");
        HttpServletRequest request = JimuSpringContextUtils.getHttpServletRequest();
        String serviceUrl = jimuAiConfigBean.getServiceUrl();
        if (OkConvertUtils.isEmpty(serviceUrl)) {
            if (JmReportUtil.e.booleanValue()) {
                serviceUrl = JmReportUtil.a(request) + "/airag/flow/run";
            } else {
                if (e == null) {
                    e = JimuSpringContextUtils.getBean(JmReportTokenClient.class);
                }
                if (e != null && !c(e.getUsername())) {
                    throw new JimuReportException(
                            "默认AI能力每日调用次数已达上限（50次），如需继续使用请联系JeecgBoot AiFlow服务");
                }
                a.warn("未集成JeecgBoot AiFlow服务，当前使用默认方式调用次数限制");
                serviceUrl = "https://api.qiaoqiaoyun.com/airag/flow/run";
            }
        }
        String response = a(serviceUrl, null, params);
        if (response == null) {
            throw new JimuReportException("调用AI服务失败，内容为空");
        }
        response = a(response);
        response = response.replace("“", "\\\"").replace("”", "\\\"").replace("、", ":");
        try {
            JSONObject json = JSONObject.parseObject(response);
            if (json.getBoolean("success").booleanValue()) {
                return json.getJSONObject("result");
            }
            throw new RuntimeException(json.getString("message"));
        } catch (Exception ex) {
            a.error("Error parsing AI service response", ex);
            throw new RuntimeException("Error parsing AI service response: " + ex.getMessage());
        }
    }

    public static String a(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("[\\u3000\\u00A0\\u200B\\u200C\\u200D\\u200E\\u200F\\u202F\\u2060\\uFEFF]", "");
    }

    public String b(String dbType) {
        String type = "";
        if (OkConvertUtils.isEmpty(dbType)) {
            type = org.jeecg.modules.jmreport.common.util.e.getDatabaseType();
        } else {
            type = org.jeecg.modules.jmreport.common.util.e.b(dbType);
        }
        return type;
    }

    public static String a(String url, String token, Map<String, Object> body) {
        HttpHeaders headers = JmReportUtil.a();
        if (headers == null) {
            headers = new HttpHeaders();
        }
        if (e == null) {
            e = JimuSpringContextUtils.getBean(JmReportTokenClient.class);
        }
        if (OkConvertUtils.isEmpty(token) && e != null) {
            token = e.getToken();
        }
        headers.add("X-Access-Token", token);
        headers.add("token", token);
        JmReportUtil.a(headers);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        JmReportUtil.c(url);
        JmReportUtil.setHttpSession(headers);
        try {
            ResponseEntity<String> response = f.exchange(url, HttpMethod.POST, entity, String.class);
            return response.getBody();
        } catch (Exception ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new RuntimeException("调用AI服务失败:响应超时");
            }
            if (ex instanceof HttpClientErrorException.NotFound) {
                HttpStatusCode status = ((HttpClientErrorException.NotFound) ex).getStatusCode();
                if (HttpStatus.NOT_FOUND.equals(status) && JmReportUtil.e.booleanValue()) {
                    throw new RuntimeException("请确认JeecgBoot版本不低于v3.8.0，且已集成AiFlow模块。");
                }
            }
            a.error("POST请求 URL={} , api调用专用 error: {}", url, ex.getMessage());
            return null;
        }
    }

    private boolean c(String username) {
        String today = LocalDate.now().toString();
        String key = username + "-" + today;
        synchronized (c) {
            Integer count = (Integer) localCache.a(key);
            if (count == null) {
                count = 0;
                localCache.a(key, count);
            }
            localCache.a(key, 1);
            return count < 50;
        }
    }
}
