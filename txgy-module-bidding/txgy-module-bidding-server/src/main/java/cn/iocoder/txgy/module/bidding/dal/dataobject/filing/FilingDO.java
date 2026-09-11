package cn.iocoder.txgy.module.bidding.dal.dataobject.filing;
// 规范依据：《招投标模块开发规范》五章；05 §2.1（备案受理记录）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 备案受理记录（T 轨）。表：bidding_filing（DDL 见 sql/history/2026-09-03-filing.sql）。
 */
@TableName("bidding_filing")
@Data
@EqualsAndHashCode(callSuper = true)
public class FilingDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID) // 雪花主键：表 id 无自增（见 DDL），显式注解压过全局 AUTO 策略
    private Long id;
    /** 备案类型（幂等键①） */
    private String filingType;
    /** 项目编号（幂等键②） */
    private String projectCode;
    /** 招标项目编号 */
    private String tenderProjectCode;
    /** 统一交易标识码 */
    private String unifiedDealCode;
    /** 招标方式（代码） */
    private String tenderMode;
    /** 组织形式（代码） */
    private String orgForm;
    /** 监督部门代码 */
    private String superviseDeptCode;
    /** 监督部门名称 */
    private String superviseDeptName;
    /** 提交时间 */
    private LocalDateTime submitTime;
    /** data_timestamp（05 交换口径） */
    private LocalDateTime dataTimestamp;
    /** 扩展/附件快照（JSON） */
    private String extraJson;
    /** 受理状态 PENDING/ACCEPTED/REJECTED */
    private String status;
    /** 备案受理号（唯一） */
    private String receiptNo;
    /** 受理（回推）时间 */
    private LocalDateTime receiptTime;
    /** 监管回推意见 */
    private String opinion;

}
