package cn.iocoder.txgy.module.training.dal.dataobject.analytics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部API接口配置 DO
 *
 * 对应表 training_external_api_config（95 外部API接口管理 / 96 外部系统对接鉴权限流）
 * // TODO[模块九-外部对接] 接口鉴权、限流、降级、黑名单的执行逻辑后续实现
 *
 * @author 云培训学院课题小组
 */
@TableName("training_external_api_config")
@KeySequence("training_external_api_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExternalApiConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 接入方名称
     */
    private String appName;
    /**
     * 接口鉴权 AppKey
     */
    private String appKey;
    /**
     * 接口鉴权 AppSecret
     */
    private String appSecret;
    /**
     * 限流 QPS
     */
    private Integer rateLimitQps;
    /**
     * 降级开关（false-正常 true-已降级）
     */
    private Boolean degradeSwitch;
    /**
     * IP 黑名单（逗号分隔）
     */
    private String blacklist;
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
