package cn.iocoder.txgy.module.hrm.dal.mysql.contract;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmContractTemplateMapper extends BaseMapperX<HrmContractTemplateDO> {

    default List<HrmContractTemplateDO> selectList() {
        return selectList(new LambdaQueryWrapperX<HrmContractTemplateDO>()
                .orderByAsc(HrmContractTemplateDO::getCategory)
                .orderByAsc(HrmContractTemplateDO::getSort)
                .orderByDesc(HrmContractTemplateDO::getDefaultStatus)
                .orderByDesc(HrmContractTemplateDO::getId));
    }

    /**
     * 按分类获得模板列表
     */
    default List<HrmContractTemplateDO> selectListByCategory(Integer category) {
        return selectList(new LambdaQueryWrapperX<HrmContractTemplateDO>()
                .eq(HrmContractTemplateDO::getCategory, category)
                .orderByAsc(HrmContractTemplateDO::getSort)
                .orderByDesc(HrmContractTemplateDO::getDefaultStatus)
                .orderByDesc(HrmContractTemplateDO::getId));
    }

    /**
     * 按合同类型获得启用模板：优先默认模板，其次最新创建的一条
     */
    default HrmContractTemplateDO selectEnabledByType(Integer contractType) {
        return selectOne(new LambdaQueryWrapperX<HrmContractTemplateDO>()
                .eq(HrmContractTemplateDO::getContractType, contractType)
                .eq(HrmContractTemplateDO::getStatus, 0)
                .orderByDesc(HrmContractTemplateDO::getDefaultStatus)
                .orderByDesc(HrmContractTemplateDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 获得租户默认启用模板：优先 default_status = 1
     */
    default HrmContractTemplateDO selectDefaultEnabled() {
        return selectOne(new LambdaQueryWrapperX<HrmContractTemplateDO>()
                .eq(HrmContractTemplateDO::getStatus, 0)
                .orderByDesc(HrmContractTemplateDO::getDefaultStatus)
                .orderByAsc(HrmContractTemplateDO::getContractType)
                .orderByDesc(HrmContractTemplateDO::getId)
                .last("LIMIT 1"));
    }

}
