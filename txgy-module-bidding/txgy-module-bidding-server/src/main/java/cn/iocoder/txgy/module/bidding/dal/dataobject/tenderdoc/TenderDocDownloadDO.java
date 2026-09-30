package cn.iocoder.txgy.module.bidding.dal.dataobject.tenderdoc;

// 规范依据：《招投标模块开发规范》§5 数据层（T 轨租户隔离）；《电子招标投标全流程说明书》§5
//   + 《实施条例》第21条（澄清修改应书面通知所有获取招标文件的潜在投标人——下载名单是通知的事实基础）
// 关联方式：tender_project_code + bid_section_code 定位；file_id 为一致性依据（复用 FileApi）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 招标文件下载记录（下载名单）。表：bidding_tender_doc_download。
 *
 * <p>业务目标：记录「谁在何时下载了哪个标段的招标文件」，作为澄清/修改定向通知
 * （条例第21条）与异议期限计算的事实基础；下载名单按招标项目/标段聚合查询。
 * 文件本体经 FileApi 上传换取 fileId，本表只记下载事实。
 */
@TableName("bidding_tender_doc_download")
@Data
@EqualsAndHashCode(callSuper = true)
public class TenderDocDownloadDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 招标项目编号（TENDER_PROJECT_CODE） */
    private String tenderProjectCode;
    /** 标段编号（BID_SECTION_CODE，空=项目级） */
    private String bidSectionCode;
    /** 关联附件行ID（bidding_attachment.id，招标文件） */
    private Long attachmentId;
    /** FileApi 文件对象主键（一致性依据） */
    private Long fileId;
    /** 投标主体ID（bidding_subject.id） */
    private Long subjectId;
    /** 下载用户ID */
    private Long userId;
    /** 下载时间（名单排序与异议期限计算依据） */
    private LocalDateTime downloadTime;
    /** 下载来源IP */
    private String ip;
    /** 备注 */
    private String remark;
}
