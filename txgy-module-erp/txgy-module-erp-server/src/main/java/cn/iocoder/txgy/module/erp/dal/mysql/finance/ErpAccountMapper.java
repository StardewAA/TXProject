package cn.iocoder.txgy.module.erp.dal.mysql.finance;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.account.ErpAccountPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpAccountDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * ERP 结算账户 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpAccountMapper extends BaseMapperX<ErpAccountDO> {

    @Select("SELECT * FROM erp_account WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ErpAccountDO selectByIdForUpdate(@Param("id") Long id);

    default PageResult<ErpAccountDO> selectPage(ErpAccountPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpAccountDO>()
                .likeIfPresent(ErpAccountDO::getName, reqVO.getName())
                .likeIfPresent(ErpAccountDO::getNo, reqVO.getNo())
                .eqIfPresent(ErpAccountDO::getRemark, reqVO.getRemark())
                .orderByDesc(ErpAccountDO::getId));
    }

    default ErpAccountDO selectByDefaultStatus() {
        return selectOne(ErpAccountDO::getDefaultStatus, true);
    }

    default List<ErpAccountDO> selectListByStatus(Integer status) {
        return selectList(ErpAccountDO::getStatus, status);
    }

    default int addBalance(Long id, BigDecimal amount) {
        return update(new LambdaUpdateWrapper<ErpAccountDO>()
                .eq(ErpAccountDO::getId, id)
                .setSql("balance = balance + " + amount.toPlainString()));
    }

}
