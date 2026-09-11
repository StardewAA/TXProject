package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelMultiPostCheckDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 多岗合规校验结论 Mapper（只追加）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(f)（结论留痕可追溯）。
 * <p>设计遵循/针对：不提供 update/delete 方法，校验结论一旦产生不可改写（追责依据）。
 * <p>业务目标：多岗兼任的合规判断可被事后核查。
 */
@Mapper
public interface PersonnelMultiPostCheckMapper extends BaseMapperX<PersonnelMultiPostCheckDO> {
}
