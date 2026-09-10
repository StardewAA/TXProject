package cn.iocoder.txgy.module.training.dal.dataobject.learning;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文档阅读明细 DO
 *
 * <p>对应表 training_doc_read_log：学员阅读文档的逐页停留明细，用于「端到端阅读」
 * 完成判定（覆盖页数 ≥ 80% 且单页停留 ≥ 5 秒才计为已覆盖）。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_doc_read_log")
@KeySequence("training_doc_read_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocReadLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 学习会话 ID
     */
    private Long sessionId;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 课程媒资 ID（文档媒资）
     */
    private Long mediaId;
    /**
     * 页号（从 1 开始）
     */
    private Integer pageNo;
    /**
     * 文档总页数（由前端 PDF 渲染引擎上报，同一会话内以最后一次上报为准）
     */
    private Integer totalPages;
    /**
     * 本页累计停留秒数（同一会话内累计）
     */
    private Integer staySeconds;
    /**
     * 最近一次上报时间
     */
    private LocalDateTime reportAt;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}
