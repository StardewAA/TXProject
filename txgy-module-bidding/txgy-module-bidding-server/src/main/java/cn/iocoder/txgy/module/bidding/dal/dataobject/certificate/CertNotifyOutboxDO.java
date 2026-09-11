package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知发送发件箱（T 轨 · 可靠投递）。表：cert_notify_outbox（DDL 见 sql/2026-09-05-cert-alert.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十一 表12：与业务同事务写入，通知不丢不重
 * （UNIQUE biz_type+biz_id+channel+receiver）；PENDING→SENDING→SUCCESS/FAILED（重试退避）→DEAD 死信。
 * <p>设计遵循/针对：payload 为 JSON 内容快照，防事后二次渲染不一致；本表为队列语义、无 version 列
 * （设计无乐观锁字段；状态流转由发送任务顺序推进，靠状态条件更新防重复取件）。
 */
@TableName("cert_notify_outbox")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertNotifyOutboxDO extends TenantBaseDO {

    /** 主键（雪花 ID；=设计 outbox_id） */
    private Long id;
    /** 业务类型 code：ALERT/CHECK_REMIND/REVIEW_REMIND */
    private String bizType;
    /** 关联业务主键（alert_id 等） */
    private Long bizId;
    /** 渠道 code：EMAIL/SMS/IN_APP/DINGTALK/WEBHOOK */
    private String channel;
    /** 接收方（L3，落库脱敏） */
    private String receiver;
    /** 发送内容快照（JSON 字符串） */
    private String payload;
    /** 发送状态 code */
    private String sendStatus;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetry;
    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;
    /** 最近一次错误信息 */
    private String lastError;
    /** 发送成功时间 */
    private LocalDateTime sentAt;
}
