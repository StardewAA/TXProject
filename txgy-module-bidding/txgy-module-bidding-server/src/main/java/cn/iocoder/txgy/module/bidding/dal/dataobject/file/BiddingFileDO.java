package cn.iocoder.txgy.module.bidding.dal.dataobject.file;

// 规范依据：《招投标模块开发规范》§5 三-5（业务表只记 file_id/sha256/size；url 不作一致性依据）
//   + 红线 8（不存裸 url）+ 红线 6（禁止跨库 SQL）；《文件接口收敛方案-后端实施篇-2026-09-20》
// 关联方式：file_id → infra_file.id（业务表落库关联键）；本表只登记「存储坐标 + 三件套 + 病毒态」

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件登记（file_id → 存储坐标）。表：bidding_file（DDL 见 sql/history/2026-09-20-bidding-file.sql）。
 *
 * <p>业务目标：把「按 fileId 取文件」的能力收在招投标侧——上传/补登记时解析并登记存储坐标
 * （config_id + path，**不是可访问 url**），下载时据此走 infra 下载端点取字节，
 * 前端全程拿不到裸 url（红线 8），业务账号也不再需要 infra:file:query。
 */
@TableName("bidding_file")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingFileDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 文件编号（infra_file.id；业务表落库关联键） */
    private Long fileId;
    /** 存储配置编号（infra 文件配置；解析自 url） */
    private Long configId;
    /** 存储路径（对象存储内相对路径；非可访问地址） */
    private String path;
    /** 原始文件名 */
    private String name;
    /** MIME 类型 */
    private String type;
    /** 文件大小（字节） */
    private Long size;
    /** 文件 SHA-256（按 URL 补登记时无法计算，为 null） */
    private String sha256;
    /** 病毒扫描状态（UNKNOWN/CLEAN/INFECTED；INFECTED 拒绝下载） */
    private String virusStatus;
    /** 登记来源（UPLOAD/BY_URL/SERVER） */
    private String source;
}
