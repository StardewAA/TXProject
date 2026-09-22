package cn.iocoder.txgy.module.mes.service.qc.iqc;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.qc.iqc.vo.MesQcIqcPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.qc.iqc.vo.MesQcIqcSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.defectrecord.MesQcDefectRecordDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.iqc.MesQcIqcDO;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MES 来料检验单（IQC） Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesQcIqcService {

    /**
     * 创建来料检验单
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createIqc(@Valid MesQcIqcSaveReqVO createReqVO);

    /**
     * 自动生成来料检验单草稿（来源单据触发）
     *
     * <p>幂等：同一来源行已存在检验单时直接返回已有编号，不重复创建；
     * 物料未配置 IQC 检验模板时跳过（返回 null），不阻断来源单流程。
     * 使用独立事务（REQUIRES_NEW），生成失败不影响来源单提交。
     *
     * @param sourceDocType 来源单据类型（MesBizTypeConstants）
     * @param sourceDocId   来源单据 ID
     * @param sourceLineId  来源单据行 ID
     * @param sourceDocCode 来源单据编号（冗余）
     * @param vendorId      供应商 ID
     * @param itemId        物料 ID
     * @param quantity      本次接收数量
     * @param receiveDate   来料日期
     * @return 检验单编号；物料未配置模板等场景返回 null
     */
    Long generateIqcDraft(Integer sourceDocType, Long sourceDocId, Long sourceLineId, String sourceDocCode,
                          Long vendorId, Long itemId, BigDecimal quantity, LocalDateTime receiveDate);

    /**
     * 更新来料检验单
     *
     * @param updateReqVO 更新信息
     */
    void updateIqc(@Valid MesQcIqcSaveReqVO updateReqVO);

    /**
     * 完成来料检验单
     *
     * @param id 编号
     */
    void finishIqc(Long id);

    /**
     * 删除来料检验单
     *
     * @param id 编号
     */
    void deleteIqc(Long id);

    /**
     * 校验来料检验单存在
     *
     * @param id 编号
     * @return 来料检验单
     */
    MesQcIqcDO validateIqcExists(Long id);

    /**
     * 获得来料检验单
     *
     * @param id 编号
     * @return 来料检验单
     */
    MesQcIqcDO getIqc(Long id);

    /**
     * 获得来料检验单分页
     *
     * @param pageReqVO 分页查询
     * @return 来料检验单分页
     */
    PageResult<MesQcIqcDO> getIqcPage(MesQcIqcPageReqVO pageReqVO);

    /**
     * 根据缺陷记录重新计算主表的缺陷统计（含行级下沉）
     *
     * @param iqcId   来料检验单 ID
     * @param records 缺陷记录列表
     */
    void recalculateDefectStats(Long iqcId, List<MesQcDefectRecordDO> records);

    /**
     * 批量获取来料检验单 Map
     *
     * @param ids 编号集合
     * @return 来料检验单 Map
     */
    Map<Long, MesQcIqcDO> getIqcMap(Collection<Long> ids);

    /**
     * 根据供应商 ID 统计来料检验单数量
     *
     * @param vendorId 供应商 ID
     * @return 数量
     */
    Long getIqcCountByVendorId(Long vendorId);

}
