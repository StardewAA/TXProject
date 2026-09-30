package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * MCP 服务 DO：登记外部 MCP server（stdio / sse / streamable-http），其工具由底座以
 * {@code mcp__{name}__{tool}} 注入 function calling。
 */
@TableName(value = "bidding_bid_agent_mcp_server", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BidAgentMcpServerDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 服务名（租户内唯一） */
    private String name;

    /** 传输：stdio / sse / streamable-http */
    private String transport;

    private String command;

    /** JSON 数组 */
    private String args;

    /** JSON 对象（值加密存储） */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String env;

    private String url;

    /** JSON 对象（值加密存储） */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String headers;

    private Integer timeoutSeconds;

    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;

    /** tools/list 结果快照 */
    private String toolSnapshotJson;

    private LocalDateTime lastCheckAt;

    private String lastCheckResult;

    private String remark;
}
