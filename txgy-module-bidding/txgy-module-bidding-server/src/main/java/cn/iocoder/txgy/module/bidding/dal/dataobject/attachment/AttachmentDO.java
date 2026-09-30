package cn.iocoder.txgy.module.bidding.dal.dataobject.attachment;
// 规范依据：《招投标模块开发规范》§5 三-5（文件经 FileApi，库内只记 file_id+sha256+size，URL 非一致性依据）
//   + 红线 8（附件三件套）+ api/vo/AttachmentSetVO（附件集结构）+ api/vo/AttachmentItemVO（35 类附件类型字典）
//   + 用户裁定 2026-09-24：针对主键的专门附件表（一对 N）；URL/对象存储承载。
// 关联方式：biz_type + biz_code（业务编号）一对 N；不侵入四实体表。

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * bidding 通用附件（项目/招标项目/标段/公告 一对 N）。表：bidding_attachment。
 *
 * <p>file_id 为一致性依据（FileApi 主键）；url 仅作预览（规范 §5 三-5：URL 非一致性依据）。
 * virus_status 对齐外部公告附件口径（UNKNOWN/CLEAN/INFECTED），下载走 FileApi 签名 URL。
 */
@TableName("bidding_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
public class AttachmentDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID) // 雪花主键：表 id 无自增（见 DDL），显式注解压过全局 AUTO 策略
    private Long id;
    /** 归属实体：PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT/BID_TRACK/PREQUAL_APPLY（后两者见契约《投标与资格预审多文件化-2026-10-02》R1） */
    private String bizType;
    /** 归属实体业务编号（project_code/tender_project_code/bid_section_code/notice_code/track.id/apply.id） */
    private String bizCode;
    /** 附件类型（35 类字典 AttachmentItemVO：TENDER_FILE/WIN_BID_NOTICE…） */
    private String attachmentType;
    /** 附件集名称（AttachmentSetVO.ATTACHMENT_NAME） */
    private String attachmentName;
    /** FileApi 文件对象主键（一致性依据） */
    private Long fileId;
    /** 对象存储访问地址（SOCIAL 公开，规范 AttachmentSetVO.URL；预览用） */
    private String url;
    /** 文件名（AttachmentSetVO.ATTACHMENT_FILE_NAME） */
    private String fileName;
    /** 文件哈希（SHA-256，红线 8 三件套） */
    private String sha256;
    /** 文件大小（字节） */
    private Long size;
    /** MIME 类型 */
    private String mimeType;
    /** 病毒扫描状态：UNKNOWN/CLEAN/INFECTED */
    private String virusStatus;
    /** 同实体内附件序号（展示顺序） */
    private Integer seq;
    /** 备注 */
    private String remark;

}