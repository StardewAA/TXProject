package cn.iocoder.txgy.module.cms.service.review;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

@Service
public class ReviewService {
    private static final ErrorCode NOT_FOUND=new ErrorCode(1_041_001_000,"审查记录不存在或无权访问");
    private static final ErrorCode ALREADY_ADMITTED=new ErrorCode(1_041_001_001,"文档已经入库，修改请新建审查版本");
    private static final ErrorCode INVALID_EVIDENCE=new ErrorCode(1_041_001_002,"请提供官方来源、效力依据、实施日期和核验意见");
    private final ReviewRepository repository;
    private final ReviewEngine engine;
    public ReviewService(ReviewRepository repository,ReviewEngine engine) {this.repository=repository;this.engine=engine;}

    @Transactional(rollbackFor=Exception.class)
    public Report submit(long tenant,Long user,Metadata metadata,Parsed parsed,String filename,byte[] original) {
        String id=UUID.randomUUID().toString();
        Report report=engine.review(id,Instant.now().toString(),metadata,parsed.text(),parsed.issues(),null);
        repository.insert(tenant,new Stored(report,parsed.text(),filename,parsed.issues(),user,List.of(),List.of()),original);
        return report;
    }
    public Stored get(long tenant,String id) {
        Stored stored=repository.get(tenant,id,false);
        if(stored==null) throw exception(NOT_FOUND);
        return stored;
    }
    public List<Report> list(long tenant,int limit) {return repository.list(tenant,Math.max(1,Math.min(limit,100)));}
    public List<SubmissionStatus> listBySubmitter(long tenant,Long user,int limit) {
        return repository.listBySubmitter(tenant,user,Math.max(1,Math.min(limit,100)));
    }
    public byte[] original(long tenant,String id) {get(tenant,id);return repository.original(tenant,id);}

    /** 清单已在控制器完整校验；任一数据库写入失败时整批事务回滚。解析失败仍保存为单独退回报告。 */
    @Transactional(rollbackFor=Exception.class)
    public BatchResult submitBatch(long tenant,Long user,List<BatchDocument> documents) {
        String batchId=UUID.randomUUID().toString();
        List<BatchItemResult> items=new ArrayList<>(documents.size());
        for(BatchDocument document:documents) {
            Report report=submit(tenant,user,document.metadata(),document.parsed(),document.fileName(),document.original());
            items.add(new BatchItemResult(document.fileName(),report));
        }
        return new BatchResult(batchId,items.size(),List.copyOf(items));
    }

    /** 独立核验人员对照官方全文后登记依据，再自动重跑全部检查。通过才在同一事务写知识库。 */
    @Transactional(rollbackFor=Exception.class)
    public Report verify(long tenant,Long reviewer,String id,EvidenceRequest request) {
        Stored stored=repository.get(tenant,id,true);
        if(stored==null) throw exception(NOT_FOUND);
        if(stored.report().admitted()) return stored.report(); // 重试不产生重复文档或切片
        Metadata official=request.officialMetadata();
        if(reviewer==null || !ReviewEngine.officialUrl(official.sourceUrl()) || !ReviewEngine.officialUrl(request.validitySourceUrl())
                || official.effectiveDate()==null || request.note().isBlank()) throw exception(INVALID_EVIDENCE);
        Evidence evidence=new Evidence(official,ReviewEngine.hash(request.officialText()),request.validitySourceUrl(),request.note(),reviewer,engine.today());
        List<Evidence> history=new ArrayList<>(stored.evidenceHistory());history.add(evidence);
        Report r=engine.review(id,stored.report().createdAt(),stored.report().metadata(),stored.text(),stored.parsingIssues(),evidence);
        List<Chunk> chunks=r.decision()==Decision.FAIL?List.of():ReviewEngine.chunk(stored.text());
        boolean passed=r.decision()!=Decision.FAIL;
        Report result=new Report(r.id(),r.createdAt(),r.metadata(),r.textHash(),r.decision(),r.score(),r.scores(),r.issues(),evidence,passed,chunks.size());
        Stored next=new Stored(result,stored.text(),stored.filename(),stored.parsingIssues(),stored.submittedBy(),List.copyOf(history),chunks);
        if(passed) repository.admit(tenant,next);
        repository.update(tenant,next);
        return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public Report resubmit(long tenant,Long user,String previousId,Submission input) {
        Stored previous=get(tenant,previousId);
        // New immutable submission retains the rejected version and its report.
        return submit(tenant,user,input.metadata(),new Parsed(input.text(),List.of()),"修订自 "+previous.report().id()+".txt",input.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
