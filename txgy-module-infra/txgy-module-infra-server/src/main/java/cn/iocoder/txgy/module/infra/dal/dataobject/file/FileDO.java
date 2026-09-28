package cn.iocoder.txgy.module.infra.dal.dataobject.file;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文件表
 * 每次文件上传，都会记录一条记录到该表中
 *
 * @author OPENLAB BS
 */
@TableName("infra_file")
@KeySequence("infra_file_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore // 文件表为全局表：租户归属由 tenantId 字段显式表达，不做 DB 层自动租户过滤
public class FileDO extends BaseDO {

    /**
     * 编号，数据库自增
     */
    private Long id;
    /**
     * 配置编号
     *
     * 关联 {@link FileConfigDO#getId()}
     */
    private Long configId;
    /**
     * 原文件名
     */
    private String name;
    /**
     * 路径，即文件名
     */
    private String path;
    /**
     * 访问地址
     */
    private String url;
    /**
     * 文件的 MIME 类型，例如 "application/octet-stream"
     */
    private String type;
    /**
     * 文件大小
     */
    private Long size;

    // ========== 访问控制相关字段（文件安全，2026-09-24 新增） ==========

    /**
     * 租户编号
     *
     * 上传时由可信登录身份写入（{@code LoginUser#getTenantId()}），非客户端传入。
     */
    private Long tenantId;
    /**
     * 访问类型
     *
     * 枚举 {@link cn.iocoder.txgy.module.infra.enums.file.FileAccessTypeEnum}
     * 约定：上传后默认 = 2（私有），仅服务端绑定可置为 1（公开）。
     */
    private Integer accessType;
    /**
     * 绑定状态
     *
     * 枚举 {@link cn.iocoder.txgy.module.infra.enums.file.FileBindStatusEnum}
     */
    private Integer bindStatus;
    /**
     * 业务类型（服务端归一化）
     *
     * 枚举名见 {@link cn.iocoder.txgy.module.infra.enums.file.FileBizTypeEnum}
     */
    private String bizType;
    /**
     * 绑定的业务单据编号（业务模块在落库时通过 RPC 写入）
     */
    private String bizId;
    /**
     * 上传者用户类型
     *
     * 枚举 {@link cn.iocoder.txgy.framework.common.enums.UserTypeEnum}
     */
    private Integer ownerUserType;
    /**
     * 上传者用户编号
     */
    private Long ownerUserId;
    /**
     * 绑定后的可读参与方，格式 {@code 用户类型:用户编号}，多个以英文逗号分隔
     *
     * 例如 {@code "1:1001,2:5"} 表示会员 1001 与后台用户 5 均可读。
     */
    private String bindParticipants;
    /**
     * 待绑定可读截止时间
     *
     * 仅 {@code bindStatus = 0（待绑定）} 时有效：此时间之前上传者本人可读，之后按孤儿文件清理。
     */
    private LocalDateTime bindExpireTime;

    // ========== OSS 提升补传追踪（2026-09-25 新增） ==========

    /**
     * OSS 提升状态
     *
     * 0=无/未提升（默认），1=处理中，2=已成功提升到公开 OSS（config_id 已变为 OSS 配置），3=失败。
     * 用于识别"已确认公开但内容仍停留在 master、未进入公共桶"的 stranded 文件，
     * 以便运维/定时任务可追踪、可安全重试补传。
     */
    private Integer promoteStatus;
    /**
     * OSS 提升累计失败次数（避免无限重试，运维可观测）
     */
    private Integer promoteFailCount;
    /**
     * OSS 提升最后一次失败信息（截断存储，便于排障）
     */
    private String promoteLastError;

}
