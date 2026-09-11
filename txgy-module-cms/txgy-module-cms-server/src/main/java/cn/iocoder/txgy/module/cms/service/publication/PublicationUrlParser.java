package cn.iocoder.txgy.module.cms.service.publication;

import cn.iocoder.txgy.module.cms.service.rag.CmsMetadataExtractor;
import cn.iocoder.txgy.module.cms.service.review.ReviewEngine;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Kind;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Metadata;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Validity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_FETCH_FAILED;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_INVALID;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_NOT_DOCUMENT;

@Service
public class PublicationUrlParser {
    private static final int MAX_PAGE_BYTES=2*1024*1024;
    private final ObjectProvider<CmsMetadataExtractor> extractorProvider;
    private final HttpClient httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public PublicationUrlParser(ObjectProvider<CmsMetadataExtractor> extractorProvider) {
        this.extractorProvider=extractorProvider;
    }

    public ParseResult parse(String rawUrl) {
        URI uri=validated(rawUrl);
        if (isLandingPage(uri)) throw exception(PUBLICATION_URL_NOT_DOCUMENT);
        try {
            for(int redirects=0;redirects<4;redirects++) {
                HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                        .header("User-Agent","TxgyCMS/1.0 compliance-import")
                        .header("Accept","text/html,application/xhtml+xml,text/plain").GET().build();
                HttpResponse<InputStream> response=httpClient.send(request,HttpResponse.BodyHandlers.ofInputStream());
                if(response.statusCode()>=300 && response.statusCode()<400) {
                    String location=response.headers().firstValue("location").orElseThrow();
                    uri=validated(uri.resolve(location).toString());
                    continue;
                }
                if(response.statusCode()<200 || response.statusCode()>=300) {
                    throw exception(PUBLICATION_URL_FETCH_FAILED);
                }
                byte[] bytes=response.body().readNBytes(MAX_PAGE_BYTES+1);
                if(bytes.length>MAX_PAGE_BYTES) throw exception(PUBLICATION_URL_FETCH_FAILED);
                // Let Jsoup honor HTTP/meta charset declarations. Some older government pages
                // still use GBK and must not become garbled review text.
                Document document=Jsoup.parse(new ByteArrayInputStream(bytes),null,uri.toString());
                document.select("script,style,noscript,nav,footer").remove();
                String title=document.title().strip();
                String text=document.body()==null?document.text():document.body().wholeText().strip();
                if(text.length()<80) throw exception(PUBLICATION_URL_NOT_DOCUMENT);
                CmsMetadataExtractor extractor=extractorProvider.getIfAvailable();
                if(extractor!=null) {
                    CmsMetadataExtractor.Extraction extraction=extractor.extract(uri.toString(),title,text);
                    return new ParseResult(extraction.metadata(),extraction.text(),true,null);
                }
                Metadata metadata=new Metadata(title.isBlank()?"待补全文档标题":title,"",uri.getHost(),uri.toString(),
                        Kind.POLICY,Validity.UNKNOWN,null,null,false);
                return new ParseResult(metadata,text,false,"未启用千问，已完成网页正文与基础元数据解析，请人工确认补全。");
            }
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(PUBLICATION_URL_INVALID);
        } catch(java.io.IOException ex) {
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        } catch(IllegalArgumentException ex) {
            throw exception(PUBLICATION_URL_INVALID);
        }
    }

    private static URI validated(String value) {
        if(value==null || !ReviewEngine.officialUrl(value.strip())) throw exception(PUBLICATION_URL_INVALID);
        return URI.create(value.strip());
    }

    static boolean isLandingPage(URI uri) {
        String path=uri.getPath();
        return "flk.npc.gov.cn".equalsIgnoreCase(uri.getHost())
                && (path==null || path.isBlank() || "/".equals(path));
    }

    public record ParseResult(Metadata metadata,String text,boolean llmExtracted,String warning) { }
}
