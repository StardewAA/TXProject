package cn.iocoder.txgy.module.cms.service.validity;

import cn.iocoder.txgy.module.cms.config.CmsLawValidityCheckProperties;
import cn.iocoder.txgy.module.cms.service.review.ReviewEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * 只读取官方状态并返回人工核验线索，不直接改变法律效力或发布状态。
 */
@Component
public class CmsLawValidityVerifier {

    private static final String NATIONAL_LAW_HOST = "flk.npc.gov.cn";

    private final CmsLawValidityCheckProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private final HttpClient httpClient;

    public CmsLawValidityVerifier(CmsLawValidityCheckProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Verification verify(String sourceUrl) {
        if (!ReviewEngine.officialUrl(sourceUrl)) {
            return Verification.issue(IssueType.SOURCE_UNREACHABLE, null,
                    "来源地址已不符合 HTTPS 政府网站规则，请人工核验");
        }
        URI uri = URI.create(sourceUrl.strip());
        try {
            if (isNationalLawDetail(uri)) {
                return verifyNationalLaw(uri);
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("User-Agent", "Mozilla/5.0 TxgyCMS/1.0 validity-check")
                    .header("Accept", "text/html,application/xhtml+xml,application/pdf,text/plain,*/*")
                    .header("Range", "bytes=0-1023")
                    .GET().build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 400) return Verification.available();
            return Verification.issue(IssueType.SOURCE_UNREACHABLE, Integer.toString(status),
                    "官方来源返回 HTTP " + status + "，请人工确认链接是否迁移或下线");
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return Verification.issue(IssueType.SOURCE_UNREACHABLE, null,
                    "官方来源暂时无法访问：" + safeMessage(ex));
        }
    }

    private Verification verifyNationalLaw(URI sourceUri) throws Exception {
        String lawId = nationalLawId(sourceUri);
        if (lawId.isBlank()) {
            return Verification.issue(IssueType.SOURCE_UNREACHABLE, null,
                    "国家法律法规数据库链接缺少法律编号");
        }
        String base = properties.getNationalLawBaseUrl().replaceAll("/+$", "");
        URI api = URI.create(base + "/law-search/search/flfgDetails?bbbs="
                + URLEncoder.encode(lawId, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(api)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("User-Agent", "Mozilla/5.0 TxgyCMS/1.0 validity-check")
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Verification.issue(IssueType.SOURCE_UNREACHABLE,
                    Integer.toString(response.statusCode()), "国家法律法规数据库状态接口暂时不可用");
        }
        JsonNode root = jsonMapper.readTree(response.body());
        JsonNode detail = root.path("data");
        if (root.path("code").asInt() != 200 || detail.isMissingNode()
                || detail.path("title").asText().isBlank()) {
            return Verification.issue(IssueType.SOURCE_UNREACHABLE, null,
                    "国家法律法规数据库未返回有效的法律详情");
        }
        int statusCode = detail.path("sxx").asInt(Integer.MIN_VALUE);
        String remoteTitle = detail.path("title").asText();
        if (statusCode == 3) {
            return new Verification(true, null, Integer.toString(statusCode), remoteTitle,
                    "国家法律法规数据库仍标记为现行有效");
        }
        String remoteStatus = statusCode == Integer.MIN_VALUE ? null : Integer.toString(statusCode);
        return new Verification(false, IssueType.REMOTE_STATUS_REVIEW, remoteStatus, remoteTitle,
                "国家法律法规数据库状态已不是现行有效（远端状态码："
                        + Objects.toString(remoteStatus, "未知") + "），请人工核验是否废止、修改或被替代");
    }

    private static boolean isNationalLawDetail(URI uri) {
        String path = Objects.toString(uri.getPath(), "").toLowerCase();
        return NATIONAL_LAW_HOST.equalsIgnoreCase(uri.getHost())
                && ("/detail".equals(path) || "/detail.html".equals(path) || "/detail2.html".equals(path));
    }

    private static String nationalLawId(URI uri) {
        for (String part : Objects.toString(uri.getRawQuery(), "").split("&")) {
            int separator = part.indexOf('=');
            if (separator < 0) continue;
            String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            if ("id".equals(key) || "bbbs".equals(key)) {
                return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8).strip();
            }
        }
        return "";
    }

    private static String safeMessage(Exception ex) {
        String value = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    public enum IssueType {
        SOURCE_UNREACHABLE,
        REMOTE_STATUS_REVIEW,
        STATUS_MISMATCH
    }

    public record Verification(boolean healthy, IssueType issueType, String remoteStatus,
                               String remoteTitle, String message) {
        static Verification available() {
            return new Verification(true, null, null, null, "官方来源可以正常访问");
        }

        static Verification issue(IssueType type, String remoteStatus, String message) {
            return new Verification(false, type, remoteStatus, null, message);
        }
    }
}
