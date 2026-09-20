package cn.iocoder.txgy.module.hrm.dal.mysql.company;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmCompanyProfileMapper extends BaseMapperX<HrmCompanyProfileDO> {

    default List<HrmCompanyProfileDO> selectList() {
        return selectList(new LambdaQueryWrapperX<HrmCompanyProfileDO>()
                .orderByDesc(HrmCompanyProfileDO::getDefaultStatus)
                .orderByAsc(HrmCompanyProfileDO::getSort)
                .orderByAsc(HrmCompanyProfileDO::getId));
    }

    /**
     * 获得默认用工主体：优先 default_status = 1，其次取最早创建的一条
     */
    default HrmCompanyProfileDO selectDefaultProfile() {
        return selectOne(new LambdaQueryWrapperX<HrmCompanyProfileDO>()
                .orderByDesc(HrmCompanyProfileDO::getDefaultStatus)
                .orderByAsc(HrmCompanyProfileDO::getId)
                .last("LIMIT 1"));
    }

}
