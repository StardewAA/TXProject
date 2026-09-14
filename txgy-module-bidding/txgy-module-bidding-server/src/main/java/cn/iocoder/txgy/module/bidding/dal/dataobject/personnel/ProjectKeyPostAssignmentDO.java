package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 项目关键岗位任职流水（表 project_key_post_assignment）。
 *
 * <p>规范出处：用户业务口径"增加对同一项目关键岗位人员频繁变更的监控（防挂靠/管理失控）"；
 * 《电子资质七大模块技术方案.md》§1.1(e)/§1.3。
 * <p>为什么按"项目+岗位+序号"建唯一键：频繁变更统计必须能精确定位"同一个项目同一个岗位的第 N 次变更"，
 * 唯一键既保证序号不重复（并发登记时由数据库兜底），也让统计口径无需依赖时间戳精度。
 * <p>业务目标：关键岗位换人可量化，超阈值自动产出挂靠嫌疑待办。
 */
@TableName("project_key_post_assignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectKeyPostAssignmentDO extends TenantBaseDO {

    private Long id;
    private Long projectId;
    private String tenderProjectCode;
    /** 关键岗位 code（KeyPostEnum） */
    private String postCode;
    private Long personId;
    private LocalDate startAt;
    /** 任职结束（NULL=在任） */
    private LocalDate endAt;
    /** 该岗位第几次变更（从 1 起） */
    private Integer changeSeq;
    private String changeReason;
}
