package cn.iocoder.txgy.module.bidding.dal.mysql.subject;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectCompanyDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 所属企业 Mapper。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2（Mapper 继承 BaseMapperX）；租户条件由平台 TenantDatabaseInterceptor
 * 按 TenantBaseDO 自动拼接（§5 五-1），本类不做手写租户过滤。
 */
@Mapper
public interface SubjectCompanyMapper extends BaseMapperX<SubjectCompanyDO> {

    /**
     * 按统一社会信用代码查询（同租户内唯一，uk_company_uscc；用于查重幂等）。
     */
    default SubjectCompanyDO selectByUscc(String uscc) {
        if (uscc == null || uscc.isBlank()) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SubjectCompanyDO>()
                .eq(SubjectCompanyDO::getUscc, uscc)
                .last("LIMIT 1"));
    }

    /**
     * 按企业名称查询（同租户内 USCC 为空时按名称判重）。
     */
    default SubjectCompanyDO selectByCompanyName(String companyName) {
        return selectOne(new LambdaQueryWrapper<SubjectCompanyDO>()
                .eq(SubjectCompanyDO::getCompanyName, companyName)
                .last("LIMIT 1"));
    }

    /**
     * 按企业类型查询是否存在（用于"本企业 SELF 每租户仅一条"校验）。
     */
    default SubjectCompanyDO selectByCompanyType(String companyType) {
        return selectOne(new LambdaQueryWrapper<SubjectCompanyDO>()
                .eq(SubjectCompanyDO::getCompanyType, companyType)
                .last("LIMIT 1"));
    }
}
