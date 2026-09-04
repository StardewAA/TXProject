package cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 安全环保检测-污染判定履历 DO
 *
 * 一条判定从创建到复核的完整操作时间线：CREATE(创建+AI初筛)/UPDATE(改单+AI重筛)/REVIEW(人工复核)
 * 每行 = "该次操作之后" 的物料名 + AI 初筛快照(ai_result/ai_confidence/ai_reason/suggested_storage)
 *        + 人工复核快照(review_result/storage_method/disposition/location/marked/remark)，
 * 只增不改，保证 AI 建议/判断与人工审核结果每一步都有迹可循。
 * 与 mes_set_pollution_check 一对多（check_id）。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pollution_check_log")
@KeySequence("mes_pollution_check_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesPollutionCheckLogDO extends BaseDO {

    /** 操作类型：创建(含 AI 初筛) */
    public static final String OP_CREATE = "CREATE";
    /** 操作类型：改单(含 AI 重筛) */
    public static final String OP_UPDATE = "UPDATE";
    /** 操作类型：人工复核(终态) */
    public static final String OP_REVIEW = "REVIEW";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 判定记录ID，关联 {@link MesSetPollutionCheckDO#getId()}
     */
    private Long checkId;
    /**
     * 判定记录编号(PC-...)
     */
    private String recordNo;
    /**
     * 操作类型：CREATE/UPDATE/REVIEW
     */
    private String opType;
    /**
     * 当时的物料/产品名称（AI 初筛主判据）
     */
    private String itemName;
    /**
     * 当时 AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN
     */
    private String aiResult;
    /**
     * 当时 AI 置信度(%)
     */
    private Integer aiConfidence;
    /**
     * 当时 AI 判定依据
     */
    private String aiReason;
    /**
     * 当时 AI 推荐存储方法
     */
    private String suggestedStorage;
    /**
     * 人工复核结果(仅 REVIEW 行)：CLEAN/POLLUTED
     */
    private String reviewResult;
    /**
     * 最终存储方法
     */
    private String storageMethod;
    /**
     * 处置方式
     */
    private String disposition;
    /**
     * 去向/库位
     */
    private String location;
    /**
     * 是否标记
     */
    private Boolean marked;
    /**
     * 备注/复核备注
     */
    private String remark;
    /**
     * 操作人
     */
    private String opBy;
    /**
     * 操作时间
     */
    private LocalDateTime opTime;

}
