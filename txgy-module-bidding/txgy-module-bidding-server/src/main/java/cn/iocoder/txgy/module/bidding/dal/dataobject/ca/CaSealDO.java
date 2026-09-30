package cn.iocoder.txgy.module.bidding.dal.dataobject.ca;

// 规范依据：《招投标模块开发规范》§5 数据层（T 轨租户隔离）；《电子招标投标办法》第20/30条（电子签名与签章）
//   + 《电子签名法》第13/14条（可靠电子签名，SM2 真实签名可自洽验签）
// 关联方式：document_id 为签章会话；track_id 可关联投标流程跟踪（bidding_process_track）；file_id 关联原文件

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * CA 文件签章记录（台账）。表：bidding_ca_seal。
 *
 * <p>业务目标：每次文件签章（测试配置本地真实 SM2 签名 + PDF 红章/分离式签名信息文件）直接落库，
 * 承载签章证据链（原文件指纹 SM3 + SM2 签名值 + 证书序列号 + 签章单位 + 已签章文件），
 * 台账支持展示与验签（verify_status：UNVERIFIED 未验 / PASS 通过 / FAIL 不通过）。
 */
@TableName("bidding_ca_seal")
@Data
@EqualsAndHashCode(callSuper = true)
public class CaSealDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 签章会话 documentId（SEAL-DOC-xxxx） */
    private String documentId;
    /** 原文件名 */
    private String documentName;
    /** 原文件类型（pdf / other；pdf 走 PDF 红章，其他走分离式签名信息文件） */
    private String documentType;
    /** 原文件 fileId（FileApi 主键） */
    private Long fileId;
    /** 原文件网络路径 */
    private String fileUrl;
    /** 原文件 SHA-256 */
    private String fileSha256;
    /** 文件 SM3 指纹（GB/T 32905，签名摘要） */
    private String sm3Hash;
    /** SM2 签名值（base64，对文件 SM3 指纹签名，真实可验） */
    private String signature;
    /** CA 证书序列号（统一测试账号 SN-LOCAL-CA-TEST-0001） */
    private String certSn;
    /** 签章单位名称 */
    private String orgName;
    /** 签章时间（yyyy-MM-dd HH:mm:ss） */
    private String sealTime;
    /** 已签章文件 fileId（PDF=盖章 PDF；其他=签名信息 .sig.txt） */
    private Long sealFileId;
    /** 已签章文件网络路径 */
    private String sealFileUrl;
    /** 已签章文件名 */
    private String sealFileName;
    /** 已签章文件字节数（契约 A1：供前端直接填 documentSize，免二次回查） */
    private Long sealFileSize;
    /** 关联投标流程跟踪 id（businessCode 可解析时） */
    private Long trackId;
    /** 验签状态（UNVERIFIED 未验 / PASS 通过 / FAIL 不通过） */
    private String verifyStatus;
    /** 验签时间 */
    private LocalDateTime verifyTime;
    /** 操作人用户ID（谁发起的签章） */
    private Long operatorUserId;
    /** 备注 */
    private String remark;
}
