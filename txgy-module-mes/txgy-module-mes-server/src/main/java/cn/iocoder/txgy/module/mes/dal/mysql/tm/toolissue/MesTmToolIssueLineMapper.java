package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolissue;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MesTmToolIssueLineMapper extends BaseMapperX<MesTmToolIssueLineDO> {

    default List<MesTmToolIssueLineDO> selectListByIssueId(Long issueId) {
        return selectList(MesTmToolIssueLineDO::getIssueId, issueId);
    }

    default void deleteByIssueId(Long issueId) {
        delete(MesTmToolIssueLineDO::getIssueId, issueId);
    }
}
