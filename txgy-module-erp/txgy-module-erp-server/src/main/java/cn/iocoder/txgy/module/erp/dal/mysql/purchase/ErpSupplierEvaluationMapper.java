package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierEvaluationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpSupplierEvaluationMapper extends BaseMapperX<ErpSupplierEvaluationDO> {

    default PageResult<ErpSupplierEvaluationDO> selectPage(ErpSupplierEvaluationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpSupplierEvaluationDO>()
                .likeIfPresent(ErpSupplierEvaluationDO::getNo, reqVO.getNo())
                .eqIfPresent(ErpSupplierEvaluationDO::getSupplierId, reqVO.getSupplierId())
                .likeIfPresent(ErpSupplierEvaluationDO::getEvalPeriod, reqVO.getEvalPeriod())
                .eqIfPresent(ErpSupplierEvaluationDO::getGrade, reqVO.getGrade())
                .betweenIfPresent(ErpSupplierEvaluationDO::getEvalDate, reqVO.getEvalDate())
                .orderByDesc(ErpSupplierEvaluationDO::getId));
    }

    default ErpSupplierEvaluationDO selectByNo(String no) { return selectOne(ErpSupplierEvaluationDO::getNo, no); }
}
