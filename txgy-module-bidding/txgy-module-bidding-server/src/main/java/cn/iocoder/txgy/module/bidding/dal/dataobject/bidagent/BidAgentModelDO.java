package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 云端模型配置 DO（全局一套：tenant_id=0；key 由平台 EncryptTypeHandler 加密落库）
 */
@TableName(value = "bidding_bid_agent_model", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidAgentModelDO extends TenantBaseDO {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID)
    private Long id;

    /** 配置名称（租户内唯一） */
    private String name;

    /** 协议：openai-compat */
    private String provider;

    /** OpenAI 兼容 base-url */
    private String baseUrl;

    /** 模型名 */
    private String model;

    /** API Key（加密存储） */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String apiKey;

    /** 温度 */
    private BigDecimal temperature;

    /** 单条回复最大 token */
    private Integer maxTokens;

    /** 单次调用超时（秒） */
    private Integer timeoutSeconds;

    /** 失败重试次数 */
    private Integer maxRetries;

    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;

    private String remark;
}
