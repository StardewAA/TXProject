package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工档案 Mapper。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2（Mapper 继承 BaseMapperX、分页/条件拼装走 Wrapper）、
 * §5 五-1（租户条件由平台 TenantDatabaseInterceptor 按 TenantBaseDO 自动拼接，本类不手写租户过滤）。
 * <p>为什么这样写：一人一档判重、投标配证候选集、身份证到期预警三类查询都是固定口径，收敛到 Mapper，
 * 服务层只表达业务语义（可测试、可复用）。
 * <p>业务目标：支撑"员工档案检索/一人一档判重/配证候选池/身份证到期预警"四个业务动作。
 */
@Mapper
public interface PersonnelProfileMapper extends BaseMapperX<PersonnelProfileDO> {

    /** 员工编号查重（UK 口径：租户 + 企业 + 员工编号）。 */
    default PersonnelProfileDO selectByPersonNo(Long entId, String personNo) {
        return selectOne(new LambdaQueryWrapper<PersonnelProfileDO>()
                .eq(PersonnelProfileDO::getEntId, entId)
                .eq(PersonnelProfileDO::getPersonNo, personNo)
                .last("LIMIT 1"));
    }

    /**
     * 按身份证盲索引反查（一人一档判重）。
     * 盲索引为 HMAC 值，库内不存明文身份证，故等值查询只能走盲索引列（v3.3 §十 注、开发规范 §4.8）。
     */
    default PersonnelProfileDO selectByIdCardBlindIndex(String blindIndex) {
        return selectOne(new LambdaQueryWrapper<PersonnelProfileDO>()
                .eq(PersonnelProfileDO::getIdCardBlindIndex, blindIndex)
                .last("LIMIT 1"));
    }

    /**
     * 投标配证候选池：本企业在职人员。
     * 只取 ACTIVE（PersonnelStatusEnum 口径），上限 1000 条防止全表扫描（开发规范 §4.6 慢查询约束）。
     */
    default List<PersonnelProfileDO> selectActiveCandidates(Long entId) {
        return selectList(new LambdaQueryWrapper<PersonnelProfileDO>()
                .eq(PersonnelProfileDO::getEntId, entId)
                .eq(PersonnelProfileDO::getPersonnelStatus, "ACTIVE")
                .orderByAsc(PersonnelProfileDO::getId)
                .last("LIMIT 1000"));
    }

    /** 按 id 批量取档案（配证结果回显用，避免 N+1 查询）。 */
    default List<PersonnelProfileDO> selectByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PersonnelProfileDO>().in(PersonnelProfileDO::getId, ids));
    }

    /**
     * 身份证有效期进入预警窗口的档案（用户明确要求：身份证到期提前预警）。
     * 口径与证照/资质预警一致：valid_to ∈ (today, today+maxDays]。
     */
    default List<PersonnelProfileDO> selectExpiringIdCards(LocalDate today, int maxDays) {
        return selectList(new LambdaQueryWrapper<PersonnelProfileDO>()
                .isNotNull(PersonnelProfileDO::getIdCardValidTo)
                .gt(PersonnelProfileDO::getIdCardValidTo, today)
                .le(PersonnelProfileDO::getIdCardValidTo, today.plusDays(maxDays))
                .orderByAsc(PersonnelProfileDO::getIdCardValidTo));
    }
}
