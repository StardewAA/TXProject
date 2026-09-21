package cn.iocoder.txgy.module.erp.service.sale;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.*;
import java.util.*;
public interface ErpCostResultService {
    Long create(ErpCostResultSaveReqVO reqVO); void update(ErpCostResultSaveReqVO reqVO); void delete(List<Long> ids);
    void submit(Long id, Long userId);
    /** 反审批造价结果（审批中 / 已审批未转换 → 草稿），并联动取消 BPM 在途流程实例 */
    void reverseApprove(Long id, Long userId, String reason);
    void updateAuditStatus(Long id, Integer bpmStatus);

    /**
     * 转换造价结果。
     *
     * <p>convertedType = 2（销售订单）时会<b>真实创建销售订单</b>并回填 convertedId；
     * 其余类型仅做标记。原实现只写 converted_type 就返回，是空壳。</p>
     *
     * @param reqVO 转换入参
     * @return 转换生成的对象编号（销售订单编号）；非建单类型返回 null
     */
    Long convert(ErpCostResultConvertReqVO reqVO);
    ErpCostResultDO get(Long id); PageResult<ErpCostResultDO> getPage(ErpCostResultPageReqVO reqVO);
    List<ErpCostResultItemDO> getItems(Collection<Long> ids);
}
