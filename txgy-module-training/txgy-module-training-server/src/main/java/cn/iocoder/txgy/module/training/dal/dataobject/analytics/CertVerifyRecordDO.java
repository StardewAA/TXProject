package cn.iocoder.txgy.module.training.dal.dataobject.analytics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证书验真记录 DO
 *
 * 对应表 training_cert_verify_record（97 证书公开验真：免登录访问留痕）
 * // TODO[模块九-证书验真] 真实证书有效性校验逻辑后续实现
 *
 * @author 云培训学院课题小组
 */
@TableName("training_cert_verify_record")
@KeySequence("training_cert_verify_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertVerifyRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 被验真的证书编号
     */
    private String certCode;
    /**
     * 验真结果：0-未查询到 1-有效 2-已过期 3-已吊销
     */
    private Integer verifyResult;
    /**
     * 吊销原因
     */
    private String revokeReason;
    /**
     * 验真来源 IP
     */
    private String verifyIp;
    /**
     * 验真来源 UA
     */
    private String userAgent;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
