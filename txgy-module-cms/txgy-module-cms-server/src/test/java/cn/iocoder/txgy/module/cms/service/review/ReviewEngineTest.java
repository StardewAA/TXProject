package cn.iocoder.txgy.module.cms.service.review;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;
import static org.assertj.core.api.Assertions.*;

class ReviewEngineTest {
    static final String TEXT="第一条 为规范测试文档的来源管理，确保所有内容均有明确来源，建立完整的原文核验制度。\n第二条 文档提交人应当提交完整的原文材料，由具有核验权限的人员核查发布机关、文号和版本沿革，并如实登记核验结果。";
    static final Metadata META=new Metadata("测试法规","测试令第1号","测试发布机关","https://www.gov.cn/example",Kind.LAW,Validity.EFFECTIVE,LocalDate.of(2020,1,1),null,false);
    static ReviewEngine engine() {return new ReviewEngine(Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"),ZoneOffset.UTC));}
    static Evidence evidence(Metadata metadata,String text) {return new Evidence(metadata,ReviewEngine.hash(text),"https://www.gov.cn/example-history","测试官方依据",2L,LocalDate.of(2026,9,8));}
    static Report report(Metadata metadata,String text,Evidence evidence) {return engine().review("id","now",metadata,text,List.of(),evidence);}

    @Test void officialDomainAloneNeverPasses() {
        assertThat(report(META,TEXT,null).decision()).isEqualTo(Decision.FAIL);
        assertThat(ReviewEngine.officialUrl("https://www.gov.cn.attacker.example/a")).isFalse();
        assertThat(ReviewEngine.officialUrl("https://www.gov.cn@127.0.0.1/a")).isFalse();
        assertThat(ReviewEngine.officialUrl("file:///etc/passwd")).isFalse();
        assertThat(ReviewEngine.officialUrl("http://www.gov.cn/a")).isFalse();
    }
    @Test void exactEvidencePassesButTamperingBlocks() {
        assertThat(report(META,TEXT,evidence(META,TEXT)).decision()).isEqualTo(Decision.PASS);
        assertThat(report(META,TEXT+"篡改",evidence(META,TEXT)).issues()).extracting(Issue::code).contains("TEXT_MISMATCH");
        assertThat(report(META,TEXT+"\uFFFD",evidence(META,TEXT+"\uFFFD")).decision()).isEqualTo(Decision.FAIL);
    }
    @Test void historicalRequiresExplicitArchiveAndValidDate() {
        Metadata old=new Metadata(META.title(),META.documentNo(),META.issuer(),META.sourceUrl(),Kind.LAW,Validity.REPEALED,META.effectiveDate(),LocalDate.of(2026,8,14),false);
        assertThat(report(old,TEXT,evidence(old,TEXT)).decision()).isEqualTo(Decision.FAIL);
        Metadata archive=new Metadata(old.title(),old.documentNo(),old.issuer(),old.sourceUrl(),old.kind(),old.validity(),old.effectiveDate(),old.expiryDate(),true);
        assertThat(report(archive,TEXT,evidence(archive,TEXT)).decision()).isEqualTo(Decision.WARNING);
    }
    @Test void expiredEvidenceAndMissingArticlesBlockRegardlessOfScore() {
        Evidence stale=new Evidence(META,ReviewEngine.hash(TEXT),META.sourceUrl(),"old",2L,LocalDate.of(2026,1,1));
        assertThat(report(META,TEXT,stale).issues()).extracting(Issue::code).contains("STALE_EVIDENCE");
        String missing=TEXT.replace("第二条","第三条");
        assertThat(report(META,missing,evidence(META,missing)).issues()).extracting(Issue::code).contains("ARTICLE_SEQUENCE");
    }
    @Test void chunksPreserveEveryCharacterAndOffsets() {
        String text=TEXT.repeat(70)+"😀结尾";
        var chunks=ReviewEngine.chunk(text);
        assertThat(chunks.stream().map(Chunk::text).reduce("",String::concat)).isEqualTo(text);
        int end=0;
        for(Chunk c:chunks) {assertThat(c.startOffset()).isEqualTo(end);assertThat(c.text()).isEqualTo(text.substring(c.startOffset(),c.endOffset()));end=c.endOffset();}
        assertThat(end).isEqualTo(text.length());
    }
}
