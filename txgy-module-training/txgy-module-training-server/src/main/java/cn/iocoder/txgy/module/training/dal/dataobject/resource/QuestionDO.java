package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试题 DO
 *
 * 对应表 training_question（单选/多选/判断/实操问答/案例分析/填空/简答）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_question")
@KeySequence("training_question_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 题型：1 单选 2 多选 3 判断 4 实操问答 5 案例分析
     */
    private Integer qtype;
    /**
     * 题干
     */
    private String stem;
    /**
     * 选项 JSON：[{"key":"A","text":"...","isCorrect":true}]
     */
    private String optionsJson;
    /**
     * 正确答案（判断/问答/案例分析存文本）
     */
    private String answer;
    /**
     * 解析
     */
    private String analysis;
    /**
     * 难度：1 易 2 中 3 难
     */
    private Integer difficulty;
    /**
     * 所属分类 ID（资源分类 question 类型，支持三级）
     */
    private Long categoryId;
    /**
     * 状态：1 启用 2 停用
     */
    private Integer status;
    /**
     * 创建人 ID
     */
    private Long creatorId;
    /**
     * 创建人姓名快照
     */
    private String creatorName;

    @TableLogic
    private Boolean deleted;

}
