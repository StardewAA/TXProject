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
 * 学习防挂机验证挑战 DO
 *
 * <p>对应表 training_verify_challenge：会话累计有效学习达到验证间隔后下发数学算式题，
 * 学员须在心跳周期内作答，服务端以 SHA-256 摘要比对答案。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_verify_challenge")
@KeySequence("training_verify_challenge_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class VerifyChallengeDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 学习会话 ID
     */
    private Long sessionId;
    /**
     * 验证问题（数学算式，如 3+4*2=?）
     */
    private String question;
    /**
     * 正确答案 SHA-256 摘要（小写十六进制）
     */
    private String answerHash;
    /**
     * 下发时间
     */
    private LocalDateTime issuedAt;
    /**
     * 作答时间
     */
    private LocalDateTime answeredAt;
    /**
     * 是否通过
     */
    private Boolean passed;
    /**
     * 已尝试次数（3 次失败判定作弊，会话置无效）
     */
    private Integer attempts;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}
