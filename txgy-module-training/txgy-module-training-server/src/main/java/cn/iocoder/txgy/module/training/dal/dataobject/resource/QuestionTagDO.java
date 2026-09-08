package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试题-标签关联 DO
 *
 * 对应表 training_question_tag
 *
 * @author 云培训学院课题小组
 */
@TableName("training_question_tag")
@KeySequence("training_question_tag_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionTagDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 试题 ID
     */
    private Long questionId;
    /**
     * 标签 ID
     */
    private Long tagId;

    @TableLogic
    private Boolean deleted;

}
