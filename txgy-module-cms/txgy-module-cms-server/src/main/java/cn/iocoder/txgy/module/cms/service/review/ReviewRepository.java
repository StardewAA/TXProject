package cn.iocoder.txgy.module.cms.service.review;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

/** 显式租户条件；事务由服务层控制。JSON快照保留每次审查和核验依据。 */
@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbc;
    public ReviewRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void insert(long tenant, Stored stored, byte[] original) {
        jdbc.update("INSERT INTO cms_document_review (id,tenant_id,snapshot,original_file,decision,created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                stored.report().id(),tenant,JsonUtils.toJsonString(stored),original,stored.report().decision().name());
    }
    public Stored get(long tenant,String id,boolean lock) {
        List<Stored> results=jdbc.query("SELECT snapshot FROM cms_document_review WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),
                (rs,row)->JsonUtils.parseObject(rs.getString(1),Stored.class),tenant,id);
        return results.isEmpty()?null:results.getFirst();
    }
    public void update(long tenant,Stored stored) {
        if(jdbc.update("UPDATE cms_document_review SET snapshot=?,decision=? WHERE tenant_id=? AND id=?",
                JsonUtils.toJsonString(stored),stored.report().decision().name(),tenant,stored.report().id())!=1)
            throw new IllegalStateException("审查记录更新失败");
    }
    public List<Report> list(long tenant,int limit) {
        return jdbc.query("SELECT snapshot FROM cms_document_review WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ?",
                (rs,row)->JsonUtils.parseObject(rs.getString(1),Stored.class).report(),tenant,limit);
    }
    public List<Stored> listStored(long tenant,int limit) {
        return jdbc.query("SELECT snapshot FROM cms_document_review WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ?",
                (rs,row)->JsonUtils.parseObject(rs.getString(1),Stored.class),tenant,limit);
    }
    public List<SubmissionStatus> listBySubmitter(long tenant, Long submittedBy, int limit) {
        return jdbc.query("""
                        SELECT r.snapshot, COALESCE(d.published, 0)
                        FROM cms_document_review r
                        LEFT JOIN cms_knowledge_document d
                          ON d.tenant_id = r.tenant_id AND d.review_id = r.id
                        WHERE r.tenant_id = ?
                        ORDER BY r.created_at DESC, r.id DESC
                        LIMIT 500
                        """,
                        (rs, row) -> java.util.Map.entry(
                                JsonUtils.parseObject(rs.getString(1), Stored.class), rs.getBoolean(2)), tenant).stream()
                .filter(entry -> Objects.equals(entry.getKey().submittedBy(), submittedBy))
                .limit(limit)
                .map(entry -> new SubmissionStatus(entry.getKey().report(), entry.getValue()))
                .toList();
    }
    public byte[] original(long tenant,String id) {
        return jdbc.queryForObject("SELECT original_file FROM cms_document_review WHERE tenant_id=? AND id=?",byte[].class,tenant,id);
    }
    public void admit(long tenant,Stored stored) {
        Report r=stored.report();
        jdbc.update("INSERT INTO cms_knowledge_document (review_id,tenant_id,title,validity,source_url,text_hash,content,published,created_at) VALUES (?,?,?,?,?,?,?,0,CURRENT_TIMESTAMP)",
                r.id(),tenant,r.metadata().title(),r.metadata().validity().name(),r.metadata().sourceUrl(),r.textHash(),stored.text());
        jdbc.batchUpdate("INSERT INTO cms_knowledge_chunk (review_id,tenant_id,ordinal,start_offset,end_offset,content) VALUES (?,?,?,?,?,?)",
                stored.chunks(),100,(ps,c)->{ps.setString(1,r.id());ps.setLong(2,tenant);ps.setInt(3,c.ordinal());ps.setInt(4,c.startOffset());ps.setInt(5,c.endOffset());ps.setString(6,c.text());});
    }
    public boolean knowledgeExists(long tenant,String id) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document WHERE tenant_id=? AND review_id=?",
                Integer.class,tenant,id);
        return count!=null && count>0;
    }
}
