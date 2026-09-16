package cn.iocoder.txgy.module.report.dal.dataobject.dataset;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 报表数据集 DO
 *
 * 报表只能通过「数据集编码 + 参数」取数，不再允许前端直接提交 SQL。
 * 数据集在服务端强制完成：租户条件注入、行数上限、查询超时、审计留痕。
 *
 * @author OPENLAB BS
 */
@TableName(value = "report_dataset", autoResultMap = true)
@KeySequence("report_dataset_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDatasetDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 数据集编码（报表引用标识，租户内唯一）
     */
    private String code;
    /**
     * 数据集名称
     */
    private String name;
    /**
     * 分类（域：MES/WMS/ERP/CRM 等）
     */
    private String category;
    /**
     * SQL 语句
     *
     * 必须 SELECT / WITH 开头；参数使用 ${name} 占位，执行时转为 PreparedStatement 参数
     */
    private String sqlText;
    /**
     * 参数定义 JSON 数组
     *
     * 结构：[{name,label,type,required,defaultValue}]
     */
    private String paramDefinitions;
    /**
     * 租户过滤列名
     *
     * 填写后由服务端强制追加等值条件（值为当前租户编号），前端无法覆盖
     */
    private String tenantColumn;
    /**
     * 最大返回行数，超过抛错
     */
    private Integer maxRows;
    /**
     * 查询超时（秒）
     */
    private Integer timeoutSeconds;
    /**
     * 状态
     *
     * 枚举 {@link cn.iocoder.txgy.framework.common.enums.CommonStatusEnum}
     */
    private Integer status;
    /**
     * 数据集责任人
     */
    private String owner;
    /**
     * 备注
     */
    private String remark;

}
