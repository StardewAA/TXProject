package cn.iocoder.txgy.module.mes.dal.mysql.wm.outsourceissue;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.wm.outsourceissue.vo.line.MesWmOutsourceIssueLinePageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.outsourceissue.MesWmOutsourceIssueLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 外协发料单行 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmOutsourceIssueLineMapper extends BaseMapperX<MesWmOutsourceIssueLineDO> {

    default PageResult<MesWmOutsourceIssueLineDO> selectPage(MesWmOutsourceIssueLinePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesWmOutsourceIssueLineDO>()
                .eqIfPresent(MesWmOutsourceIssueLineDO::getIssueId, reqVO.getIssueId())
                .orderByDesc(MesWmOutsourceIssueLineDO::getId));
    }

    default List<MesWmOutsourceIssueLineDO> selectListByIssueId(Long issueId) {
        return selectList(MesWmOutsourceIssueLineDO::getIssueId, issueId);
    }

    default void deleteByIssueId(Long issueId) {
        delete(MesWmOutsourceIssueLineDO::getIssueId, issueId);
    }

}
