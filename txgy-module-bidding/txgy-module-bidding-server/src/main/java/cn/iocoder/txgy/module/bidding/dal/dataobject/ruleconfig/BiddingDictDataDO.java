package cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务字典（表 bidding_dict_data）——电子证照域枚举的唯一动态来源。
 *
 * <p>规范出处：用户硬约束"状态码/枚举值必须支持动态配置，严禁硬编码"；契约《电子证照模块-前端接口契约-v1.0.md》§1.1
 * （GET /bidding/meta/dicts 返回 value/label/tagType/terminal/sort）。
 * <p>设计遵循/针对：① 不依赖 platform 侧 system_dict_data（跨库/跨发布节奏耦合），本模块自带字典表，
 * 与业务同库同事务；② color_type 即契约 tagType，terminal 让前端据此禁用状态流转按钮；
 * ③ 字典缺失时服务层按枚举兜底（新枚举值不会因字典未配而"消失"）。
 * <p>业务目标：运营改中文名/标签色/新增枚举后前端无需发版；避免"前端 switch 穷举枚举"遇到新值渲染异常。
 */
@TableName("bidding_dict_data")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingDictDataDO extends TenantBaseDO {

    private Long id;
    /** 字典类型 code（cert_status/file_category/…） */
    private String dictType;
    /** 枚举值 code（提交给后端的值） */
    private String dictValue;
    /** 中文名（前端直接展示） */
    private String dictLabel;
    /** 标签色 code：success/warning/error/info/default */
    private String colorType;
    /** 是否终态（终态禁止再流转） */
    private Boolean terminal;
    /** 展示顺序 */
    private Integer sort;
    /** 启用开关 */
    private Boolean enabled;
    private String remark;
    @Version
    private Integer version;
}
