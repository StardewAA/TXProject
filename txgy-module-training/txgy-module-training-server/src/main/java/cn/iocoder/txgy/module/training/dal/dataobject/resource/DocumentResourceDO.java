package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档资源 DO
 *
 * 对应表 training_document_resource（PDF/JPG/PNG ≤50MB，在线预览、阅读上报）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_document_resource")
@KeySequence("training_document_resource_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentResourceDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 文档标题
     */
    private String title;
    /**
     * 所属分类 ID
     */
    private Long categoryId;
    /**
     * 原始文件名
     */
    private String fileName;
    /**
     * 文件相对存储路径（uploads/resource 下）
     */
    private String filePath;
    /**
     * 文件类型：pdf / jpg / png
     */
    private String fileType;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 文档总页数（上传时自动识别）
     */
    private Integer pageCount;
    /**
     * 归属业务分类（行业新规/国标解读/防腐工艺等，自由文本）
     */
    private String bizCategory;
    /**
     * 标签（逗号分隔，易版轻量实现）
     */
    private String tags;
    /**
     * 预估阅读分钟（预留学时换算）
     */
    private Integer estMinutes;
    /**
     * 状态：1 草稿 2 上线 3 下架 4 归档
     */
    private Integer status;
    /**
     * 预览次数
     */
    private Integer viewCount;
    /**
     * 累计阅读停留秒数
     */
    private Integer readSeconds;
    /**
     * 上传人 ID
     */
    private Long uploaderId;
    /**
     * 上传人姓名快照
     */
    private String uploaderName;

    @TableLogic
    private Boolean deleted;

}
