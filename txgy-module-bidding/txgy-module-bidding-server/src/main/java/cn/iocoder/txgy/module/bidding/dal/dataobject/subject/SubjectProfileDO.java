package cn.iocoder.txgy.module.bidding.dal.dataobject.subject;
// [依据] 主体信息档案（企业租户级）：招标投标法 第8/25/26条（招标人/投标人定义与投标能力）、
//       《电子招标投标办法》第24条（投标人在平台注册登记、如实递交信息并经验证——授权后仍须完善信息才能投标）、
//       国家/陕西 V2.0 数据规范 表89 法人基本信息数据集（单位名称/统一社会信用代码/法定代表人/联系人等必填口径）。
// 一企业一租户：本表 T 轨（TenantBaseDO），租户各自维护本企业主体档案；同一租户可有 BIDDER/TENDERER 两条档案（角色可兼）。

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 主体信息档案（T 轨，企业租户自维护）。表：bidding_subject_profile（DDL 见 sql/2026-09-09-subject.sql）。
 *
 * <p>核心列=投标资格门/列表所需；权威字段全集扩展（按主体类型差异数据集）暂存 {@code extJson}，
 * 由 {@code SubjectPayloadUtils} 规整（key=规范英文名称，延续 notice fieldsJson 做法）。
 */
@TableName("bidding_subject_profile")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectProfileDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 主体类型：BIDDER/TENDERER（SubjectTypeEnum；AGENCY 预留未启用） */
    private String subjectType;
    /** 单位名称（表89 LEGAL_NAME，必填；投标人/招标人主体名称） */
    private String subjectName;
    /** 统一社会信用代码（表89 LEGAL_CODE C18；GB 32100-2015 编码规则含校验码，保存时强校验） */
    private String uscc;
    /** 法定代表人姓名（表89 LEGAL_REPRESENT，必填） */
    private String legalPersonName;
    /** 法人机构类别代码（表89 LEGAL_TYPE C1，参考 14.4.2 法人机构类别；保存时格式校验） */
    private String legalPersonTypeCode;
    /** 行政区划代码（表89 REGION_CODE C6，GB/T 2260，市级代码；保存时格式校验） */
    private String regionCode;
    /** 行业代码（表89 LEGAL_INDUSTRY C3，GB/T 4754-2011：1 位门类字母+2 位大类数字；可选） */
    private String industryCode;
    /** 注册资本（表89 REG_CAPITAL N..20,6；可选） */
    private BigDecimal regCapital;
    /** 注册资本币种（表89 REG_CAP_CURRENCY，GB/T 12406-2008 数字码，默认 156 人民币；可选） */
    private String regCapCurrency;
    /** 联系人（表89 LEGAL_CONTACT，必填） */
    private String contactName;
    /** 联系人电话（表89 LEGAL_CONTACT_PHONE，必填；实名联络/通知义务送达对象） */
    private String contactPhone;
    /** 营业执照影像附件 file_id（FileApi 对象主键；实名凭据，可选——电子办法第24条验证信息真实性，影像留档更佳） */
    private Long licenseFileId;
    /** 营业执照影像附件名称（展示用） */
    private String licenseFileName;
    /** 权威扩展字段 JSON（key=规范英文名称；投标人/招标人扩展数据集，可选采集） */
    private String extJson;
    /** 是否信息完善（服务端按必填清单实时核算后落位；见 SubjectProfileService 必填规则） */
    private Boolean completeFlag;
    /** 最近一次达到“完善”的时间（再变不完整则清空） */
    private LocalDateTime completeTime;
    /** 备注 */
    private String remark;
}
