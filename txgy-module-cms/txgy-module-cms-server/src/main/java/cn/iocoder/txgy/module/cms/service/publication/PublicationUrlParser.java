package cn.iocoder.txgy.module.cms.service.publication;

import cn.iocoder.txgy.module.cms.service.rag.CmsMetadataExtractor;
import cn.iocoder.txgy.module.cms.service.review.ReviewParser;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Kind;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Metadata;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Severity;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Validity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_FETCH_FAILED;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_INVALID;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_NOT_DOCUMENT;

@Service
public class PublicationUrlParser {
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    private static final String NATIONAL_LAW_HOST = "flk.npc.gov.cn";
    private static final String NATIONAL_LAW_DOWNLOAD_HOST = "flkoss.obs-bj2.cucloud.cn";
    private final ObjectProvider<CmsMetadataExtractor> extractorProvider;
    private final ReviewParser reviewParser;
    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public PublicationUrlParser(ObjectProvider<CmsMetadataExtractor> extractorProvider, ReviewParser reviewParser) {
        this.extractorProvider = extractorProvider;
        this.reviewParser = reviewParser;
    }

    public ParseResult parse(String rawUrl) {
        URI uri = validated(rawUrl);
        if (isLandingPage(uri)) throw exception(PUBLICATION_URL_NOT_DOCUMENT);
        try {
            if (isNationalLawDetail(uri)) return parseNationalLawDetail(uri);
            for (int redirects = 0; redirects < 4; redirects++) {
                HttpRequest request = request(uri, "text/html,application/xhtml+xml,text/plain");
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("location").orElseThrow();
                    uri = validated(uri.resolve(location).toString());
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw exception(PUBLICATION_URL_FETCH_FAILED);
                }
                byte[] bytes = response.body().readNBytes(MAX_PAGE_BYTES + 1);
                if (bytes.length > MAX_PAGE_BYTES) throw exception(PUBLICATION_URL_FETCH_FAILED);
                // Let Jsoup honor HTTP/meta charset declarations. Some older government pages
                // still use GBK and must not become garbled review text.
                Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, uri.toString());
                document.select("script,style,noscript,nav,footer").remove();
                String title = document.title().strip();
                String text = document.body() == null ? document.text() : document.body().wholeText().strip();
                if (text.length() < 80) throw exception(PUBLICATION_URL_NOT_DOCUMENT);
                CmsMetadataExtractor extractor = extractor();
                if (extractor != null) {
                    CmsMetadataExtractor.Extraction extraction = extractor.extract(uri.toString(), title, text);
                    return new ParseResult(extraction.metadata(), extraction.text(), true, null);
                }
                Metadata metadata = new Metadata(title.isBlank() ? "待补全文档标题" : title, "", uri.getHost(),
                        uri.toString(), Kind.POLICY, Validity.UNKNOWN, null, null, false);
                return new ParseResult(metadata, text, false, "未启用千问，已完成网页正文与基础元数据解析，请人工确认补全。");
            }
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(PUBLICATION_URL_INVALID);
        } catch (java.io.IOException ex) {
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        } catch (IllegalArgumentException ex) {
            throw exception(PUBLICATION_URL_INVALID);
        }
    }

    private ParseResult parseNationalLawDetail(URI sourceUri) throws java.io.IOException, InterruptedException {
        String lawId = nationalLawId(sourceUri);
        if (lawId.isBlank()) throw exception(PUBLICATION_URL_NOT_DOCUMENT);
        String encodedId = URLEncoder.encode(lawId, StandardCharsets.UTF_8);
        JsonNode detail = getJson(URI.create("https://" + NATIONAL_LAW_HOST
                + "/law-search/search/flfgDetails?bbbs=" + encodedId)).path("data");
        if (detail.isMissingNode() || detail.path("title").asText().isBlank()) {
            throw exception(PUBLICATION_URL_NOT_DOCUMENT);
        }
        JsonNode download = getJson(URI.create("https://" + NATIONAL_LAW_HOST
                + "/law-search/download/pc?format=docx&bbbs=" + encodedId + "&fileId=")).path("data");
        URI documentUri = validatedNationalLawDownload(download.path("url").asText());
        byte[] documentBytes = getBytes(documentUri, ReviewParser.MAX_BYTES);
        var parsed = reviewParser.parse(detail.path("title").asText() + ".docx", documentBytes);
        if (parsed.text().strip().length() < 80 || parsed.issues().stream()
                .anyMatch(issue -> issue.severity() == Severity.ERROR)) {
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        }

        CmsMetadataExtractor extractor = extractor();
        CmsMetadataExtractor.Extraction extraction = extractor == null ? null
                : extractor.extract(sourceUri.toString(), detail.path("title").asText(), parsed.text());
        Metadata inferred = extraction == null ? null : extraction.metadata();
        Metadata metadata = new Metadata(
                detail.path("title").asText(),
                inferred == null ? "" : Objects.toString(inferred.documentNo(), ""),
                detail.path("zdjgName").asText(),
                sourceUri.toString(),
                Kind.LAW,
                nationalLawValidity(detail.path("sxx").asInt(), inferred == null ? Validity.UNKNOWN : inferred.validity()),
                date(detail.path("sxrq").asText(), inferred == null ? null : inferred.effectiveDate()),
                inferred == null ? null : inferred.expiryDate(),
                inferred != null && inferred.historical());
        String warning = extraction == null
                ? "已从国家法律法规数据库下载官方 DOCX 并提取完整正文，请人工确认文号和效力。"
                : "已从国家法律法规数据库下载官方 DOCX 并智能提取元数据，请人工确认后提交。";
        return new ParseResult(metadata, parsed.text(), extraction != null, warning);
    }

    private JsonNode getJson(URI uri) throws java.io.IOException, InterruptedException {
        byte[] bytes = getBytes(uri, MAX_PAGE_BYTES);
        JsonNode result = objectMapper.readTree(bytes);
        if (result.path("code").asInt() != 200) throw exception(PUBLICATION_URL_FETCH_FAILED);
        return result;
    }

    private byte[] getBytes(URI uri, int limit) throws java.io.IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(request(uri, "application/json,application/octet-stream"),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        }
        byte[] bytes = response.body().readNBytes(limit + 1);
        if (bytes.length > limit) throw exception(PUBLICATION_URL_FETCH_FAILED);
        return bytes;
    }

    private static HttpRequest request(URI uri, String accept) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 TxgyCMS/1.0 compliance-import")
                .header("Accept", accept).GET().build();
    }

    private CmsMetadataExtractor extractor() {
        return extractorProvider == null ? null : extractorProvider.getIfAvailable();
    }

    private static URI validated(String value) {
        if (value == null || value.isBlank()) throw exception(PUBLICATION_URL_INVALID);
        try {
            URI uri = URI.create(value.strip());
            String host = normalizedHost(uri);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || host.isBlank() || !host.contains(".")
                    || "localhost".equals(host) || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")
                    || isIpLiteral(host)) {
                throw exception(PUBLICATION_URL_INVALID);
            }
            ensurePublicHost(host);
            return uri;
        } catch (IllegalArgumentException ex) {
            throw exception(PUBLICATION_URL_INVALID);
        }
    }

    /**
     * URL parsing accepts public legal-information sites as source material. Official-domain enforcement remains in
     * {@link cn.iocoder.txgy.module.cms.service.review.ReviewEngine}, so non-government sources still require an
     * administrator to provide government evidence before publication.
     */
    static boolean safePublicHttpsUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.strip());
            String host = normalizedHost(uri);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && !host.isBlank() && host.contains(".")
                    && !"localhost".equals(host) && !host.endsWith(".localhost")
                    && !host.endsWith(".local") && !host.endsWith(".internal")
                    && !isIpLiteral(host);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String normalizedHost(URI uri) {
        String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}");
    }

    private static void ensurePublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw exception(PUBLICATION_URL_INVALID);
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) throw exception(PUBLICATION_URL_INVALID);
            }
        } catch (UnknownHostException ex) {
            throw exception(PUBLICATION_URL_INVALID);
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return !(first == 0
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && Byte.toUnsignedInt(bytes[2]) == 100)
                    || (first == 203 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 113));
        }
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xfe) != 0xfc;
        }
        return false;
    }

    private static URI validatedNationalLawDownload(String value) {
        try {
            URI uri = URI.create(value);
            String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !NATIONAL_LAW_DOWNLOAD_HOST.equals(host)) {
                throw exception(PUBLICATION_URL_FETCH_FAILED);
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        }
    }

    static boolean isNationalLawDetail(URI uri) {
        String path = Objects.toString(uri.getPath(), "").toLowerCase(Locale.ROOT);
        return NATIONAL_LAW_HOST.equalsIgnoreCase(uri.getHost())
                && ("/detail".equals(path) || "/detail.html".equals(path) || "/detail2.html".equals(path));
    }

    static String nationalLawId(URI uri) {
        String query = Objects.toString(uri.getRawQuery(), "");
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator < 0) continue;
            String key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            if ("id".equals(key) || "bbbs".equals(key)) {
                return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8).strip();
            }
        }
        return "";
    }

    static Validity nationalLawValidity(int code, Validity fallback) {
        // 新版国家法律法规数据库中 3 表示现行有效；其他状态仍交由人工确认，避免误判效力。
        return code == 3 ? Validity.EFFECTIVE : fallback;
    }

    private static LocalDate date(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static boolean isLandingPage(URI uri) {
        String path = uri.getPath();
        return NATIONAL_LAW_HOST.equalsIgnoreCase(uri.getHost())
                && (path == null || path.isBlank() || "/".equals(path) || "/search".equals(path));
    }

    public record ParseResult(Metadata metadata, String text, boolean llmExtracted, String warning,
                              String snapshotToken) {
        public ParseResult(Metadata metadata, String text, boolean llmExtracted, String warning) {
            this(metadata, text, llmExtracted, warning, null);
        }
        public ParseResult withSnapshotToken(String value) {
            return new ParseResult(metadata, text, llmExtracted, warning, value);
        }
    }
}
