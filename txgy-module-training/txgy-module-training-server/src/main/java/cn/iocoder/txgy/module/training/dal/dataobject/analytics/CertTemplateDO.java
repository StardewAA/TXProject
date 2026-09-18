package cn.iocoder.txgy.module.training.dal.dataobject.analytics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 培训证书模板 DO
 *
 * 对应表 training_cert_template（95 证书模板配置 / 92 个人档案证书展示）
 * // TODO[模块九-证书模板] 模板字段配置（content_json）的解析与渲染后续实现
 *
 * @author 云培训学院课题小组
 */
@TableName("training_cert_template")
@KeySequence("training_cert_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertTemplateDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 模板编码
     */
    private String code;
    /**
     * 证书类型：1-普通电子证书 2-联合认证证书
     */
    private Integer type;
    /**
     * 模板背景图地址
     */
    private String templateUrl;
    /**
     * 模板字段配置（JSON）
     */
    private String contentJson;
    /**
     * 证书有效天数
     */
    private Integer validDays;
    /**
     * 到期前复审提醒天数
     */
    private Integer reviewDays;
    /**
     * 状态：0-启用 1-停用
     */
    private Integer status;
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
