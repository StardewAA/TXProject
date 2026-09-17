package cn.iocoder.txgy.module.crm.dal.dataobject.followup;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.mybatis.core.type.LongListTypeHandler;
import cn.iocoder.txgy.framework.mybatis.core.type.StringListTypeHandler;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.txgy.module.crm.enums.DictTypeConstants;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跟进记录 DO
 *
 * 用于记录客户、联系人的每一次跟进
 *
 * @author OPENLAB BS
 */
@TableName(value = "crm_follow_up_record", autoResultMap = true)
@KeySequence("crm_follow_up_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmFollowUpRecordDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 数据类型
     *
     * 枚举 {@link CrmBizTypeEnum}
     */
    private Integer bizType;
    /**
     * 数据编号
     *
     * 关联 {@link CrmBizTypeEnum} 对应模块 DO 的 id 字段
     */
    private Long bizId;

    /**
     * 跟进类型
     *
     * 关联 {@link DictTypeConstants#CRM_FOLLOW_UP_TYPE} 字典
     */
    private Integer type;
    /**
     * 跟进内容
     */
    private String content;
    /**
     * 下次联系时间
     *
     * 含义：计划的下一次联系客户的时间（业务计划时间），并非本次跟进实际发生的时间
     */
    private LocalDateTime nextTime;

    /**
     * 实际跟进时间
     *
     * 含义：本次「实际和客户发生跟进」的时间，是判断「多久没有跟进」的唯一依据
     * （例如：客户公海回收计时以最后一次实际跟进时间为起点）
     *
     * 注意：与 {@link BaseDO#getCreateTime()}（这条跟进笔记录入系统的时间）不是一回事：
     * 允许补录历史跟进，所以它可能早于 createTime；也可能等于 createTime（写完就录）
     */
    private LocalDateTime followUpTime;

    /**
     * 图片
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> picUrls;
    /**
     * 附件
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;

    /**
     * 关联的商机编号数组
     *
     * 关联 {@link CrmBusinessDO#getId()}
     */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> businessIds;
    /**
     * 关联的联系人编号数组
     *
     * 关联 {@link CrmContactDO#getId()}
     */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> contactIds;


}
