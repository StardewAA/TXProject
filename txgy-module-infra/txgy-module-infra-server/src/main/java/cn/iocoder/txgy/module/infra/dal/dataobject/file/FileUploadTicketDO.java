package cn.iocoder.txgy.module.infra.dal.dataobject.file;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文件上传凭证（预签名直传登记用）
 *
 * 安全背景：前端直传模式下，{@code path} 由服务端生成但登记接口曾直接采信客户端传入的
 * {@code configId + path}，导致「知道他人 path 即可重新登记并冒领」。本凭证把
 * 「服务端签发」与「登记消费」绑定起来：
 * <pre>
 *   presigned-url  → 服务端为当前登录身份写入一张凭证（本条记录）
 *   /file/create   → 校验并要求消费该凭证；不存在/已过期/非本人 → 拒绝登记
 * </pre>
 *
 * @author OPENLAB BS
 */
@TableName("infra_file_upload_ticket")
@KeySequence("infra_file_upload_ticket_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore // 全局表：租户归属由 tenantId 字段显式表达，不做 DB 层自动租户过滤
public class FileUploadTicketDO extends BaseDO {

    /**
     * 编号，数据库自增
     */
    private Long id;
    /**
     * 文件配置编号
     */
    private Long configId;
    /**
     * 服务端生成的文件路径
     */
    private String path;
    /**
     * 签发时登录身份所属租户编号
     */
    private Long tenantId;
    /**
     * 签发时用户类型
     *
     * 枚举 {@link cn.iocoder.txgy.framework.common.enums.UserTypeEnum}
     */
    private Integer ownerUserType;
    /**
     * 签发时用户编号
     */
    private Long ownerUserId;
    /**
     * 凭证过期时间
     */
    private LocalDateTime expireTime;

}
