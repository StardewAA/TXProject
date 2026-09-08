package cn.iocoder.txgy.module.cms.service.review;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;
import static cn.iocoder.txgy.module.cms.service.review.ReviewEngineTest.*;
import static org.assertj.core.api.Assertions.*;

class ReviewWorkflowTest {
    AnnotationConfigApplicationContext context;
    ReviewService service;
    JdbcTemplate jdbc;
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {return new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean JdbcTemplate jdbc(DataSource ds) {return new JdbcTemplate(ds);}
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean ReviewRepository repository(JdbcTemplate jdbc) {return new ReviewRepository(jdbc);}
        @Bean ReviewEngine engineBean() {return engine();}
        @Bean ReviewService service(ReviewRepository repo,ReviewEngine engine) {return new ReviewService(repo,engine);}
    }
    @BeforeEach void setup() throws Exception {
        context=new AnnotationConfigApplicationContext(Config.class);
        service=context.getBean(ReviewService.class);jdbc=context.getBean(JdbcTemplate.class);
        Path dir=Path.of("").toAbsolutePath();
        while(!java.nio.file.Files.exists(dir.resolve("sql/cms_document_review.sql"))) dir=dir.getParent();
        try(var conn=context.getBean(DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(conn,new FileSystemResource(dir.resolve("sql/cms_document_review.sql")));
        }
    }
    @AfterEach void close() {context.close();}
    Report submit() {return service.submit(2005,1L,META,new Parsed(TEXT,List.of()),"test.txt",TEXT.getBytes());}
    EvidenceRequest proof(String text) {return new EvidenceRequest(META,text,META.sourceUrl(),"已对照官方原文和沿革核验完整性");}
    @Test void rejectionStaysInDatabaseAndPassAdmitsOnce() {
        Report r=submit();
        assertThat(r.decision()).isEqualTo(Decision.FAIL);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_document_review",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isZero();
        Report pass=service.verify(2005,2L,r.id(),proof(TEXT));
        assertThat(pass.admitted()).isTrue();
        assertThat(service.verify(2005,2L,r.id(),proof(TEXT)).admitted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM cms_knowledge_chunk",String.class)).isEqualTo(TEXT);
    }
    @Test void failedVerificationNeverEntersKnowledge() {
        Report r=submit();
        assertThat(service.verify(2005,2L,r.id(),proof(TEXT+"不同原文")).admitted()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isZero();
        assertThat(service.get(2005,r.id()).evidenceHistory()).hasSize(1);
    }
    @Test void anotherTenantCannotReadVerifyOrResubmit() {
        Report r=submit();
        assertThatThrownBy(()->service.get(1,r.id()));
        assertThatThrownBy(()->service.verify(1,2L,r.id(),proof(TEXT)));
        assertThatThrownBy(()->service.resubmit(1,2L,r.id(),new Submission(META,TEXT)));
        assertThat(service.list(1,50)).isEmpty();
    }
    @Test void chunkFailureRollsBackBothKnowledgeAndReport() {
        Report r=submit();
        jdbc.execute("ALTER TABLE cms_knowledge_chunk ADD CONSTRAINT forced_failure CHECK (ordinal<0)");
        assertThatThrownBy(()->service.verify(2005,2L,r.id(),proof(TEXT)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isZero();
        assertThat(service.get(2005,r.id()).report().admitted()).isFalse();
        assertThat(service.get(2005,r.id()).evidenceHistory()).isEmpty();
    }
    @Test void resubmitPreservesRejectedOriginal() {
        Report r=submit();
        Report retry=service.resubmit(2005,1L,r.id(),new Submission(META,TEXT+"修订"));
        assertThat(retry.id()).isNotEqualTo(r.id());
        assertThat(service.get(2005,r.id()).text()).isEqualTo(TEXT);
    }
}
