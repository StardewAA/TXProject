package cn.iocoder.txgy.module.report.dal.dataobject.dataset;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 报表数据集执行日志 DO
 *
 * 每次查询（成功或失败）都留痕：谁、何时、用哪个数据集、什么参数、返回多少行、耗时多少。
 *
 * @author OPENLAB BS
 */
@TableName(value = "report_dataset_execute_log", autoResultMap = true)
@KeySequence("report_dataset_execute_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDatasetExecuteLogDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 数据集编号；0 表示未登记的原始 SQL
     */
    private Long datasetId;
    /**
     * 数据集编码
     */
    private String datasetCode;
    /**
     * 来源
     *
     * dataset - 数据集；raw-sql - 大屏原始 SQL
     */
    private String source;
    /**
     * 查询参数 JSON
     */
    private String params;
    /**
     * 返回行数
     */
    private Integer rowCount;
    /**
     * 耗时（毫秒）
     */
    private Long durationMs;
    /**
     * 是否成功
     */
    private Boolean success;
    /**
     * 失败原因
     */
    private String errorMessage;
    /**
     * 执行人编号
     */
    private Long userId;

}
