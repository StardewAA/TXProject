package cn.iocoder.txgy.module.training.dal.dataobject.plan;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 上岗资格核查历史记录 DO
 *
 * 对应表 training_qualification_record（每次核查写入一条，支持删除 / 批量删除）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_qualification_record")
@KeySequence("training_qualification_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 平台用户 ID（人员不存在时为空）
     */
    private Long userId;
    /**
     * 核查输入/匹配到的姓名
     */
    private String userName;
    /**
     * 核查输入的手机号
     */
    private String mobile;
    /**
     * 是否具备上岗资格
     */
    private Boolean qualified;
    /**
     * 通过岗位（JSON 字符串数组，如 ["管道工","防腐工"]）
     */
    private String passedPosts;
    /**
     * 结论说明
     */
    private String reason;
    /**
     * 核查明细（培训班列表 JSON，同 QualificationRespVO.items）
     */
    private String detailJson;

}
