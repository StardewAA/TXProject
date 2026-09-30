package cn.iocoder.txgy.module.bidding.dal.dataobject.contract;

// 规范依据：《招投标模块开发规范》§5 数据层（T 轨租户隔离）；《电子招标投标全流程说明书》§11
//   + 招标投标法第46条（中标通知书发出30日内签约）、第47条（定标后15日内报告）
//   + 《实施条例》第57条（履约保证金不超过合同价10%）、第58条（合同签订5日内退还投标保证金）
// 关联方式：tender_project_code + bid_section_code 定位；file_id 为合同文件（电子签名文件）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工程合同（合同签署与项目完结）。表：bidding_contract。
 *
 * <p>业务目标：记录中标后订立的书面合同（合同价/签订时间/履约保证金/电子签名文件），
 * 登记时校验履约保证金 ≤ 合同价 10%（条例第57条）与签约 ≤ 中标通知书发出 30 日（招标投标法第46条）；
 * 作为「合同签订后 5 日内退还投标保证金」（条例第58条）与「定标后 15 日内提交书面报告」（第47条）的事实基础。
 */
@TableName("bidding_contract")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 招标项目编号（TENDER_PROJECT_CODE） */
    private String tenderProjectCode;
    /** 标段编号（BID_SECTION_CODE，空=项目级） */
    private String bidSectionCode;
    /** 合同编号（内部编号，前缀 C） */
    private String contractCode;
    /** 合同名称 */
    private String contractName;
    /** 合同价（元） */
    private BigDecimal contractPrice;
    /** 履约保证金（元） */
    private BigDecimal performanceBond;
    /** 中标通知书发出时间（签约 30 日时限的起算点） */
    private LocalDateTime winNoticeTime;
    /** 合同签订时间 */
    private LocalDateTime signTime;
    /** 合同文件 FileApi 主键（电子签名文件） */
    private Long fileId;
    /** 合同状态（SIGNED 已签 / ARCHIVED 已归档；归档后只读锁定） */
    private String status;
    /** 归档时间（SIGNED→ARCHIVED 时点；归档锁自动落库） */
    private LocalDateTime archiveTime;
    /** 保存到期时间（归档 + 15 年，自动计算落库；电子办法技术规范 存档≥15年） */
    private LocalDateTime retentionUntil;
    /** 保存期限提醒标记（NORMAL/EXPIRING_SOON/EXPIRED，自动计算） */
    private String retentionWarn;
    /** 合同一致性比对结果（MATCH 与中标结果一致 / MISMATCH 不一致标红 / NO_BASELINE 无中标基准，save 时自动计算） */
    private String compareResult;
    /** 备注 */
    private String remark;
}
