package cn.iocoder.txgy.module.erp.service.sale;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.*;
import java.util.*;
public interface ErpCostResultService {
    Long create(ErpCostResultSaveReqVO reqVO); void update(ErpCostResultSaveReqVO reqVO); void delete(List<Long> ids);
    void submit(Long id, Long userId); void updateAuditStatus(Long id, Integer bpmStatus); void convert(ErpCostResultConvertReqVO reqVO);
    ErpCostResultDO get(Long id); PageResult<ErpCostResultDO> getPage(ErpCostResultPageReqVO reqVO);
    List<ErpCostResultItemDO> getItems(Collection<Long> ids);
}
