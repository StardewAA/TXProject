package cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 安全环保检测-污染判定记录 DO
 *
 * 采购入库/生产领用/中间废弃物/成品 四环节的 AI 初筛 + 人工复核 判定记录。
 * 人工复核终态只允许：CLEAN(无污染)/POLLUTED(有污染)；AI 初筛为：
 * CLEAN/POLLUTED/UNCERTAIN(不确定)，"不确定"必须由人工复核收口。
 *
 * @author OPENLAB BS
 */
@TableName("mes_set_pollution_check")
@KeySequence("mes_set_pollution_check_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesSetPollutionCheckDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 记录编号 PC-YYYYMMDD-NNN
     */
    private String recordNo;
    /**
     * 环节：PURCHASE_INBOUND(采购入库)/MATERIAL_ISSUE(生产领用)/WASTE_INTERMEDIATE(中间废弃物)/FINISHED_PRODUCT(成品)
     */
    private String stage;
    /**
     * 关联单号（入库单/领料单/报工单/成品入库单）
     */
    private String bizNo;
    /**
     * 批次号
     */
    private String batchNo;
    /**
     * 物料/产品编码
     */
    private String itemCode;
    /**
     * 物料/产品名称
     */
    private String itemName;
    /**
     * 规格
     */
    private String itemSpec;
    /**
     * AI 初筛结果：CLEAN(无污染)/POLLUTED(有污染)/UNCERTAIN(不确定)
     */
    private String aiResult;
    /**
     * AI 置信度(%) 0-100
     */
    private Integer aiConfidence;
    /**
     * AI 判定依据
     */
    private String aiReason;
    /**
     * AI 推荐存储方法
     */
    private String suggestedStorage;
    /**
     * 人工复核结果（最终）：CLEAN(无污染)/POLLUTED(有污染)，空=待复核
     */
    private String reviewResult;
    /**
     * 最终存储方法（复核确认）
     */
    private String storageMethod;
    /**
     * 处置方式：NORMAL_INBOUND/CONTROLLED_STORAGE/ISSUE_ALLOWED/REJECT_ISSUE/REUSE/DISCHARGE/ISOLATE_STORAGE/MARKED_STORAGE
     */
    private String disposition;
    /**
     * 去向/库位
     */
    private String location;
    /**
     * 是否标记（有污染/不达标需管控的标记），0 否 1 是
     */
    private Boolean marked;
    /**
     * 复核人
     */
    private String reviewBy;
    /**
     * 复核时间
     */
    private LocalDateTime reviewTime;
    /**
     * 备注
     */
    private String remark;

}
