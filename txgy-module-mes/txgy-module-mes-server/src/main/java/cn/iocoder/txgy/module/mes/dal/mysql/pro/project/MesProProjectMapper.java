package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 项目 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectMapper extends BaseMapperX<MesProProjectDO> {

    default PageResult<MesProProjectDO> selectPage(MesProProjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProProjectDO>()
                .likeIfPresent(MesProProjectDO::getCode, reqVO.getCode())
                .likeIfPresent(MesProProjectDO::getName, reqVO.getName())
                .eqIfPresent(MesProProjectDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MesProProjectDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(MesProProjectDO::getPriority, reqVO.getPriority())
                .likeIfPresent(MesProProjectDO::getOwner, reqVO.getOwner())
                .eqIfPresent(MesProProjectDO::getDept, reqVO.getDept())
                .orderByDesc(MesProProjectDO::getId));
    }

    default MesProProjectDO selectByCode(String code) {
        return selectOne(MesProProjectDO::getCode, code);
    }

    /**
     * 按来源类型 + 来源单据号查询项目（销售订单 → 项目幂等创建使用）
     *
     * @param sourceType      来源类型
     * @param orderSourceCode 来源单据号
     * @return 项目
     */
    default MesProProjectDO selectBySourceTypeAndOrderSourceCode(Integer sourceType, String orderSourceCode) {
        return selectOne(new LambdaQueryWrapperX<MesProProjectDO>()
                .eq(MesProProjectDO::getSourceType, sourceType)
                .eq(MesProProjectDO::getOrderSourceCode, orderSourceCode));
    }

    /**
     * 按编码前缀模糊查询项目列表（PRJ 编码生成时计算当日最大序号使用）
     *
     * @param codePrefix 编码前缀
     * @return 项目列表
     */
    default java.util.List<MesProProjectDO> selectListByCodeLike(String codePrefix) {
        return selectList(new LambdaQueryWrapperX<MesProProjectDO>()
                .likeRight(MesProProjectDO::getCode, codePrefix));
    }

}
