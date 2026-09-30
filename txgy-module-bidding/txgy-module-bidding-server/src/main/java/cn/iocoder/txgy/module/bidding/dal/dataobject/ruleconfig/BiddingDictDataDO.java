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
// =====================================================================
// 【租户过滤例外】本表登记在 txgy.tenant.ignore-tables（application.yaml）。
//
// 为什么：本表是"平台默认行(tenant_id=0) + 租户覆盖行(tenant_id=N)"的**双租户模型**，
//   服务层显式写 tenant_id 谓词（先查 0 再叠加当前租户覆盖），租户语义由业务自己表达。
//   而 DO 继承 TenantBaseDO 会让租户插件**无条件追加** `AND tenant_id = <当前租户>`，
//   把显式谓词变成自相矛盾的条件。实测日志（当前租户 1）：
//     SQL : ... WHERE deleted = 0 AND (tenant_id = ? AND enabled = ?) AND tenant_id = 1
//     参数: 0(Long), true(Boolean)      ← 即 tenant_id = 0 AND tenant_id = 1
//     Total: 0
//   后果：/bidding/meta/dicts 恒为空并报 1060108008「字典类型不支持」，
//         前端所有状态下拉与角标静默回落英文 code。库里 196 行 / 44 类字典**全部** tenant_id=0，
//         所以这不是"某类字典没配"，而是**整张字典表都读不到**。
//
// ⚠️ 实测教训：**@TenantIgnore 加在 DO 上无效**。框架侧 TenantIgnoreAspect 是对**方法**做 AOP 的
//    （签名 around(ProceedingJoinPoint, TenantIgnore)），数据库层的过滤由 TenantDatabaseInterceptor
//    按 TenantProperties.ignoreTables（即配置项）决定，与 DO 注解无关。
//    先前把注解加在 DO 上、编译通过、重启后 SQL 仍是 AND tenant_id = 1 —— 白改一轮。
//
// ⚠️ 也不改 Mapper 的写法：服务层的 0 与 N 两次查询是**设计意图**（平台默认 + 租户覆盖），
//    去掉任一都会丢能力。错的是"插件在业务已自行表达租户语义的表上再插一脚"。
// =====================================================================
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
