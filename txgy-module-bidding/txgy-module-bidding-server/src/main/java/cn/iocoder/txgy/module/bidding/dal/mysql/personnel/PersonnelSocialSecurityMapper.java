package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelSocialSecurityDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 员工社保缴纳记录 Mapper。
 *
 * <p>规范出处：用户业务口径"核验社保缴纳单位与申报企业是否一致"；《电子证件全流程管理_数据库设计.md》
 * v3.3 表 15-3（social_security_status/social_security_tenant）。
 * <p>设计遵循/针对：取"最近一期"用 pay_month 倒序 + LIMIT 1，避免把全量履历拉进内存再排序；
 * 批量查询用于多员工核验，防止逐人查询造成 N+1。
 * <p>业务目标：取最近一期缴纳记录做主体一致性核验（人证分离/挂证判定）。
 */
@Mapper
public interface PersonnelSocialSecurityMapper extends BaseMapperX<PersonnelSocialSecurityDO> {

    /** 最近一期社保记录（按缴纳月份倒序；核验以最近一月为准）。 */
    default PersonnelSocialSecurityDO selectLatestByPerson(Long personId) {
        return selectOne(new LambdaQueryWrapper<PersonnelSocialSecurityDO>()
                .eq(PersonnelSocialSecurityDO::getPersonId, personId)
                .orderByDesc(PersonnelSocialSecurityDO::getPayMonth)
                .last("LIMIT 1"));
    }

    /** 某员工社保缴纳履历（全景展示与人工复核用）。 */
    default List<PersonnelSocialSecurityDO> selectByPerson(Long personId) {
        return selectList(new LambdaQueryWrapper<PersonnelSocialSecurityDO>()
                .eq(PersonnelSocialSecurityDO::getPersonId, personId)
                .orderByDesc(PersonnelSocialSecurityDO::getPayMonth));
    }

    /** 按员工批量取最近缴纳记录（批量核验用；服务层按人取首条）。 */
    default List<PersonnelSocialSecurityDO> selectByPersonIds(List<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PersonnelSocialSecurityDO>()
                .in(PersonnelSocialSecurityDO::getPersonId, personIds)
                .orderByDesc(PersonnelSocialSecurityDO::getPayMonth));
    }
}
