package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.company;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company.HrmRecruitCompanyProfileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface HrmRecruitCompanyProfileMapper extends BaseMapperX<HrmRecruitCompanyProfileDO> {

    @Select({
            "<script>",
            "SELECT id, name FROM system_tenant WHERE status = 0 AND deleted = 0 AND id IN",
            "<foreach collection='tenantIds' item='tenantId' open='(' separator=',' close=')'>",
            "#{tenantId}",
            "</foreach>",
            "</script>"
    })
    List<Map<String, Object>> selectPortalTenantNames(@Param("tenantIds") List<Long> tenantIds);

    default HrmRecruitCompanyProfileDO selectByTenantId(Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitCompanyProfileDO>()
                .eq(HrmRecruitCompanyProfileDO::getTenantId, tenantId));
    }

    default HrmRecruitCompanyProfileDO selectPortalByTenantId(Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitCompanyProfileDO>()
                .eq(HrmRecruitCompanyProfileDO::getTenantId, tenantId)
                .eq(HrmRecruitCompanyProfileDO::getStatus, 0));
    }

    default List<HrmRecruitCompanyProfileDO> selectPortalTenantIds() {
        return selectList(new LambdaQueryWrapperX<HrmRecruitCompanyProfileDO>()
                .select(HrmRecruitCompanyProfileDO::getTenantId)
                .eq(HrmRecruitCompanyProfileDO::getStatus, 0));
    }

    default List<HrmRecruitCompanyProfileDO> selectPortalByTenantIds(List<Long> tenantIds) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitCompanyProfileDO>()
                .in(HrmRecruitCompanyProfileDO::getTenantId, tenantIds)
                .eq(HrmRecruitCompanyProfileDO::getStatus, 0)
                .orderByAsc(HrmRecruitCompanyProfileDO::getSort)
                .orderByAsc(HrmRecruitCompanyProfileDO::getId));
    }
}
