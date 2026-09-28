package cn.iocoder.txgy.module.bidding.dal.dataobject.prequal;

// 规范依据：《招投标模块开发规范》§5 数据层；《电子招标投标全流程说明书》§6.3 第 3 条
//   + 《实施条例》第18条（国有资金占控股/主导项目应组建资格审查委员会）、第37条（组成规则）
// 关联方式：prequal_id → bidding_prequal.id（资格预审主记录）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资格审查委员会成员表（条例第18条：国有资金项目强制组建）。
 *
 * <p>业务目标：承载资格审查委员会成员名单（招标人代表 + 技术/经济专家）与组成校验事实基础；
 * 国有资金占控股或主导地位的依法必招项目，未组建合格委员会禁止进入审查（review 前置校验）。
 * 组成规则（条例第37条，资格审查委员会适用）：成员 5 人以上单数，技术经济专家不少于成员总数 2/3。
 */
@TableName("bidding_prequal_committee")
@Data
@EqualsAndHashCode(callSuper = true)
public class PrequalCommitteeDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 资格预审ID（bidding_prequal.id） */
    private Long prequalId;
    /** 成员姓名 */
    private String memberName;
    /** 成员类别（PrequalCommitteeMemberCategoryEnum：TENDERER_REP 招标人代表 / TECH_EXPERT 技术专家 / ECON_EXPERT 经济专家） */
    private String memberCategory;
    /** 委员会内角色（LEADER 组长 / MEMBER 成员） */
    private String memberRole;
    /** 所在单位 */
    private String memberOrg;
    /** 职称/职务 */
    private String memberTitle;
    /** 排序 */
    private Integer sort;
    /** 备注 */
    private String remark;
}
