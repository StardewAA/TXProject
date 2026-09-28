package cn.iocoder.txgy.module.bidding.dal.mysql.prequal;

// [规范依据]《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）
// [业务目标] 资格审查委员会成员表数据访问（租户过滤由框架拦截器自动追加）

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalCommitteeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PrequalCommitteeMapper extends BaseMapperX<PrequalCommitteeDO> {

    /** 按资格预审查委员会成员（按 sort 排序）。 */
    default List<PrequalCommitteeDO> selectListByPrequal(Long prequalId) {
        return selectList(PrequalCommitteeDO::getPrequalId, prequalId);
    }
}
