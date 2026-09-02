package cn.iocoder.txgy.module.hrm.dal.mysql.salary.config;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryOptionTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmSalaryOptionTemplateMapper extends BaseMapperX<HrmSalaryOptionTemplateDO> {

    default List<HrmSalaryOptionTemplateDO> selectListOrderByCode() {
        return selectList(new LambdaQueryWrapperX<HrmSalaryOptionTemplateDO>()
                .orderByAsc(HrmSalaryOptionTemplateDO::getCode));
    }

}
