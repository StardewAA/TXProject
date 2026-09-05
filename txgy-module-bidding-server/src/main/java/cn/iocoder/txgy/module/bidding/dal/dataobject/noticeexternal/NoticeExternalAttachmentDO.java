package cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal;
// 规范依据：设计文档 v0.6 §7；《招投标模块开发规范》红线 8（附件只存 file_id+sha256+size，FileApi 签名下载）

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部公告附件（G 轨）。表：bidding_notice_external_attachment。
 * 下载地址一律走 FileApi 签名 URL（virus_status 病毒扫描，先例：资质域），不直传第三方裸链。
 */
@TableName("bidding_notice_external_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalAttachmentDO extends BaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 归属外部公告主档 ID */
    private Long noticeId;
    /** FileApi 文件对象主键 */
    private Long fileId;
    /** 文件哈希（SHA-256） */
    private String sha256;
    /** 文件大小（字节） */
    private Long size;
    /** 文件名 */
    private String name;
    /** 扩展名 */
    private String ext;
    /** 外站原始下载链接（溯源） */
    private String origDownloadUrl;
    /** 病毒扫描状态：PENDING/CLEAN/INFECTED */
    private String virusStatus;

}
