package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelTrainingRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 员工培训记录 Mapper。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2（Mapper 分层）；《电子证件全流程管理_数据库设计.md》
 * v3.3 表 15-3（继续教育学时达标要素）。
 * <p>业务目标：按人查询培训与继续教育履历，供资质人员指标核验与员工资质全景展示。
 */
@Mapper
public interface PersonnelTrainingRecordMapper extends BaseMapperX<PersonnelTrainingRecordDO> {

    /** 某人培训记录（按结束日期倒序，最近的在最前）。 */
    default List<PersonnelTrainingRecordDO> selectByPerson(Long personId) {
        return selectList(new LambdaQueryWrapper<PersonnelTrainingRecordDO>()
                .eq(PersonnelTrainingRecordDO::getPersonId, personId)
                .orderByDesc(PersonnelTrainingRecordDO::getEndDate));
    }
}
