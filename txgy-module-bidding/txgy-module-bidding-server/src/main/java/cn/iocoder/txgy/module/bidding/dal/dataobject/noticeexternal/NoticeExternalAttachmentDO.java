package cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal;
// 规范依据：设计文档 v0.6 §7；《招投标模块开发规范》红线 8（附件只存 file_id+sha256+size，FileApi 签名下载）

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部公告附件（G 轨）。表：bidding_notice_external_attachment。
 * 下载地址一律走 FileApi 签名 URL（virus_status 病毒扫描，先例：资质域），不直传第三方裸链。
 */
@TenantIgnore // G 轨：表无 tenant_id 列，忽略租户拦截器（平台级数据，见 DO 注释）
@TableName("bidding_notice_external_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalAttachmentDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID) // 雪花主键：表 id 无自增（见 DDL），显式注解压过全局 AUTO 策略
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
