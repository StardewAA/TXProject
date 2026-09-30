package cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投标流程跟踪（投标方视角，T 轨）。表：bidding_process_track。
 *
 * <p>设计依据：《投标流程跟踪功能设计方案》。状态机：INTENTION→QUALIFICATION_REVIEW→PENDING_BID→BIDDED / TERMINATED。
 * 投标文件：document_hash 为 SM3 指纹（国密 GB/T 32905，用户裁定）；CA 加密/签名由外部 CA SDK 完成，本表只存结果。
 */
@TableName("bidding_process_track")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingProcessTrackDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 投标员工用户ID */
    private Long userId;
    /** 负责人用户ID（投标员工；契约 §5 ProcessTrackItemVO.ownerUserId） */
    private Long ownerUserId;
    /** 负责人姓名快照（AdminUserApi 建档时点取值，避免逐行 RPC 与改名漂移） */
    private String ownerName;
    /** 负责人职务（可空） */
    private String ownerPost;
    /** 负责人联系方式（可空） */
    private String ownerPhone;
    /** 公司名快照（平台授权 SubjectAuth.tenantName 时点取值） */
    private String tenantName;
    /** 地区（BidSection/TenderProject regionCode 时点取值） */
    private String region;
    /** 预算金额（BidSection.contractReckonPrice 时点取值） */
    private String amount;
    /** 投标截止时间（bid_end_time 时点取值） */
    private LocalDateTime deadline;
    /** 关注条目ID（关联 bidding_follow_item） */
    private Long followItemId;
    /** 维度代码（标段编号） */
    private String followKey;
    /** 维度类型（PROJECT/SECTION） */
    private String followType;
    /** 招标项目编号 */
    private String tenderProjectCode;
    /** 标段编号 */
    private String bidSectionCode;
    /** 项目名称（快照） */
    private String projectName;
    /** 发布单位（快照） */
    private String issuerName;
    /** 当前状态（BiddingProcessStateEnum code） */
    private String currentState;
    /** 上一状态 */
    private String previousState;
    /** 意向投标时间（关注时间） */
    private LocalDateTime intentionTime;
    /** 报名时间 */
    private LocalDateTime registerTime;
    /** 审核完成时间 */
    private LocalDateTime reviewTime;
    /** 进入待投标时间 */
    private LocalDateTime pendingBidTime;
    /** 已投标时间（上传投标文件时间） */
    private LocalDateTime biddedTime;
    /** 流程结束时间 */
    private LocalDateTime terminatedTime;
    /** 审核结果（ReviewResultEnum code） */
    private String reviewResult;
    /** 审核说明/不通过原因 */
    private String reviewReason;
    /** 投标文件（加密）fileId */
    private Long documentFileId;
    /** 投标文件名 */
    private String documentName;
    /** 投标文件大小（字节） */
    private Long documentSize;
    /** 投标文件指纹（SM3 十六进制，国密 GB/T 32905） */
    private String documentHash;
    /** 指纹算法（SM3） */
    private String documentHashAlgo;
    /** 投标文件数字签名（外部 CA SDK 签名，SM2，base64） */
    private String documentSignature;
    /** CA 证书序列号 */
    private String caCertNo;
    /** CA 公钥（PEM，验签用） */
    private String caPublicKey;
    /** 备注 */
    private String remark;
}
