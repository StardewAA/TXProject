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
        assertThat(r.decision()).isEqualTo(Decision.PENDING);
        assertThat(r.workflowStatus()).isEqualTo(WorkflowStatus.AI_REVIEWING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_document_review",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isZero();
        Report pass=service.verify(2005,2L,r.id(),proof(TEXT));
        assertThat(pass.admitted()).isTrue();
        assertThat(service.verify(2005,2L,r.id(),proof(TEXT)).admitted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT published FROM cms_knowledge_document",Boolean.class)).isFalse();
        assertThat(service.listBySubmitter(2005,1L,50)).singleElement()
                .satisfies(item -> {
                    assertThat(item.report().admitted()).isTrue();
                    assertThat(item.published()).isFalse();
                });
        assertThat(service.listBySubmitter(2005,99L,50)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT content FROM cms_knowledge_chunk",String.class)).isEqualTo(TEXT);
    }
    @Test void aiFailureStillEntersManualQueueAndNeverAutoPublishes() {
        Report r=submit();
        Report reviewed=service.completeAiReview(2005,r.id());
        assertThat(reviewed.decision()).isEqualTo(Decision.FAIL);
        assertThat(reviewed.workflowStatus()).isEqualTo(WorkflowStatus.PENDING_MANUAL);
        assertThat(reviewed.admitted()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isZero();
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
        service.completeAiReview(2005,r.id());
        service.manualReview(2005,2L,r.id(),new ManualReviewRequest(ManualDecision.REJECT,"请修改后重提",null));
        Report retry=service.resubmit(2005,1L,r.id(),new Submission(META,TEXT));
        assertThat(retry.id()).isNotEqualTo(r.id());
        assertThat(service.get(2005,r.id()).text()).isEqualTo(TEXT);
        assertThat(service.get(2005,r.id()).report().workflowStatus()).isEqualTo(WorkflowStatus.REJECTED);
        assertThatThrownBy(()->service.resubmit(2005,99L,r.id(),new Submission(META,TEXT+"其他人")));
    }
    @Test void batchSubmissionCreatesIndependentReports() {
        List<BatchDocument> documents=List.of(
                new BatchDocument("一.txt",META,new Parsed(TEXT,List.of()),TEXT.getBytes()),
                new BatchDocument("二.txt",new Metadata("第二份", "", META.issuer(),
                        "https://www.gov.cn/second", Kind.LAW, META.validity(), META.effectiveDate(), null, false),
                        new Parsed(TEXT+"第二份",List.of()),(TEXT+"第二份").getBytes()));
        BatchResult result=service.submitBatch(2005,1L,documents);
        assertThat(result.batchId()).matches("[a-f0-9\\-]{36}");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(BatchItemResult::fileName).containsExactly("一.txt","二.txt");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_document_review",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document",Integer.class)).isZero();
    }
    @Test void duplicateSubmissionIsRejectedBeforeInsert() {
        submit();
        assertThatThrownBy(this::submit);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_document_review",Integer.class)).isEqualTo(1);
    }
    @Test void batchDatabaseFailureRollsBackAllItems() {
        List<BatchDocument> documents=List.of(
                new BatchDocument("一.txt",META,new Parsed(TEXT,List.of()),TEXT.getBytes()),
                new BatchDocument("坏数据.txt",null,new Parsed(TEXT,List.of()),TEXT.getBytes()));
        assertThatThrownBy(()->service.submitBatch(2005,1L,documents)).isInstanceOf(NullPointerException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_document_review",Integer.class)).isZero();
    }
}
