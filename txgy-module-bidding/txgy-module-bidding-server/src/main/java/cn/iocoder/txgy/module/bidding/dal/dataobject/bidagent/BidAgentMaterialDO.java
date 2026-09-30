package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（DO 与表一一对应、驼峰字段、@TableName）
// [业务目标] 标书智能体的材料台账：专员在后台登记招标文件/企业资料/技术母本，并跟踪入库状态与片段数
// [为什么] 材料是"召回质量"的源头，必须有一个可盘点、可追踪入库结果、可重建的实体；
//          file_id 是落库唯一依据（规范 §4.5 三-5），URL 仅用于取内容入库，不落库

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@TableName("bidding_bid_material")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidAgentMaterialDO extends TenantBaseDO {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 材料角色：TENDER 招标文件 / FACT 企业资料 / TEMPLATE 技术母本 / PRICING 报价数据 */
    private String docType;
    /** 材料名称（专员可见） */
    private String name;
    /** 所属标段编号（TENDER 必填；FACT/TEMPLATE/PRICING 可空=跨标段复用） */
    private String bidSectionCode;
    /** 所属项目（PRICING 报价数据必填，其余角色可空） */
    private Long projectId;
    /** infra 文件编号（全局唯一，落库唯一依据） */
    private Long fileId;
    /** 文件访问地址（仅用于取内容入库与即时预览，不作为一致性依据） */
    private String fileUrl;
    /** 原始文件名 */
    private String fileName;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 入库状态：0 待入库 1 已入库 2 失败 */
    private Integer indexStatus;
    /** 片段数 */
    private Integer segmentCount;
    /** 入库结果或失败原因 */
    private String indexMessage;
    /** 有效期（FACT 类资质用于过期提醒） */
    private LocalDate validUntil;
    /** 备注 */
    private String remark;
}
