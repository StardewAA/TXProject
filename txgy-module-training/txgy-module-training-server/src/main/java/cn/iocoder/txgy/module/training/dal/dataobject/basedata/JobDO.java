package cn.iocoder.txgy.module.training.dal.dataobject.basedata;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位工种 DO
 *
 * 对应表 training_job（云培训学院基础数据字典）
 * 数据来源：《云培训学院-岗位工种表.xlsx》；《数据字典与一级子模块关联》
 *
 * 关联关系：
 * - learnDomainCodes -> training_course_category.code（学习域 L1 域 / L2 组）
 * - maxLevel         -> training_growth_level.code（最高成长等级）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_job")
@KeySequence("training_job_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 工种编码：OP-* 操作序列 / TC-* 技术序列 / QC-* 质检检测序列 / MG-* 管理序列 / SP-* 支持序列
     */
    private String code;
    /**
     * 工种名称
     */
    private String name;
    /**
     * 序列编码：OP 操作 / TC 技术 / QC 质检检测 / MG 管理 / SP 支持
     */
    private String seqCode;
    /**
     * 序列名称
     */
    private String seqName;
    /**
     * 业务类目（对应文档章节号，如 一/二/三/二十三）
     */
    private String bizCategory;
    /**
     * 学习域/课程组（原始文本，如 A,B01-B03 / 全域）
     */
    private String learnDomain;
    /**
     * 规范化学习域/课程组编码（逗号分隔，关联 training_course_category.code；* 表示全域）
     */
    private String learnDomainCodes;
    /**
     * 关键技能
     */
    private String keySkills;
    /**
     * 最高成长等级（关联 training_growth_level.code）
     */
    private String maxLevel;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 状态：active 启用 / disabled 停用
     */
    private String status;
    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
