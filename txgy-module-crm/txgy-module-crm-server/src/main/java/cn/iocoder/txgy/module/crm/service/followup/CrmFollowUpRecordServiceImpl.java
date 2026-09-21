package cn.iocoder.txgy.module.crm.service.followup;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.followup.vo.CrmFollowUpRecordPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.followup.vo.CrmFollowUpRecordSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.followup.CrmFollowUpRecordDO;
import cn.iocoder.txgy.module.crm.dal.mysql.business.CrmBusinessMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.followup.CrmFollowUpRecordMapper;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.txgy.module.crm.framework.permission.core.annotations.CrmPermission;
import cn.iocoder.txgy.module.crm.service.business.CrmBusinessService;
import cn.iocoder.txgy.module.crm.service.clue.CrmClueService;
import cn.iocoder.txgy.module.crm.service.contact.CrmContactService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.service.followup.bo.CrmFollowUpCreateReqBO;
import cn.iocoder.txgy.module.crm.service.permission.CrmPermissionService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.FOLLOW_UP_RECORD_DELETE_DENIED;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.FOLLOW_UP_RECORD_NOT_EXISTS;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.FOLLOW_UP_RECORD_TIME_FUTURE;

/**
 * 跟进记录 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class CrmFollowUpRecordServiceImpl implements CrmFollowUpRecordService {

    @Resource
    private CrmFollowUpRecordMapper crmFollowUpRecordMapper;

    @Resource
    @Lazy
    private CrmPermissionService permissionService;
    @Resource
    @Lazy
    private CrmBusinessService businessService;
    @Resource
    @Lazy
    private CrmClueService clueService;
    @Resource
    @Lazy
    private CrmContactService contactService;
    @Resource
    @Lazy
    private CrmContractService contractService;
    @Resource
    @Lazy
    private CrmCustomerService customerService;

    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmBusinessMapper businessMapper;
    @Resource
    private CrmClueMapper clueMapper;

    @Override
    @CrmPermission(bizTypeValue = "#createReqVO.bizType", bizId = "#createReqVO.bizId", level = CrmPermissionLevelEnum.WRITE)
    public Long createFollowUpRecord(CrmFollowUpRecordSaveReqVO createReqVO) {
        // 1. 计算「实际跟进时间」：未填写时取当前时间（兼容不传该字段的老版本前端），不允许晚于当前时间。
        //    注意它与另外两个时间的区别：
        //      - createTime    ：这条跟进笔记录入系统的时间（写笔记的那一刻），由框架自动生成；
        //      - nextTime      ：下次联系时间，业务计划时间，仅用于【待办】的今天需联系/已逾期提醒；
        //      - followUpTime  ：实际和对方发生跟进的时间，是「多久没跟进 → 客户掉入公海」的计时起点，
        //                        允许补录历史（填过去的日期），例如 9/16 补录 9/1 的拜访。
        LocalDateTime followUpTime = createReqVO.getFollowUpTime() != null ? createReqVO.getFollowUpTime()
                : LocalDateTime.now();
        if (followUpTime.isAfter(LocalDateTime.now())) {
            throw exception(FOLLOW_UP_RECORD_TIME_FUTURE);
        }

        // 2. 创建更进记录
        CrmFollowUpRecordDO record = BeanUtils.toBean(createReqVO, CrmFollowUpRecordDO.class)
                .setFollowUpTime(followUpTime);
        crmFollowUpRecordMapper.insert(record);

        // 3. 更新 bizId 对应的记录（透传「实际跟进时间」，避免下游各自取当前时间把公海计时错误重置）
        if (ObjUtil.equal(CrmBizTypeEnum.CRM_CUSTOMER.getType(), record.getBizType())) { // 更新客户跟进信息
            customerService.updateCustomerFollowUp(record.getBizId(), record.getFollowUpTime(), record.getNextTime(),
                    record.getContent());
        }
        if (ObjUtil.equal(CrmBizTypeEnum.CRM_BUSINESS.getType(), record.getBizType())) { // 更新商机跟进信息
            businessService.updateBusinessFollowUp(record.getBizId(), record.getFollowUpTime(), record.getNextTime(),
                    record.getContent());
        }
        if (ObjUtil.equal(CrmBizTypeEnum.CRM_CLUE.getType(), record.getBizType())) { // 更新线索跟进信息
            clueService.updateClueFollowUp(record.getBizId(), record.getFollowUpTime(), record.getNextTime(),
                    record.getContent());
        }
        if (ObjUtil.equal(CrmBizTypeEnum.CRM_CONTACT.getType(), record.getBizType())) { // 更新联系人跟进信息
            contactService.updateContactFollowUp(record.getBizId(), record.getFollowUpTime(), record.getNextTime(),
                    record.getContent());
        }
        if (ObjUtil.equal(CrmBizTypeEnum.CRM_CONTRACT.getType(), record.getBizType())) { // 更新合同跟进信息
            contractService.updateContractFollowUp(record.getBizId(), record.getFollowUpTime(), record.getNextTime(),
                    record.getContent());
        }

        // 4.1 更新 contactIds 对应的记录，只更新 nextTime
        if (CollUtil.isNotEmpty(createReqVO.getContactIds())) {
            contactService.updateContactContactNextTime(createReqVO.getContactIds(), createReqVO.getNextTime());
        }
        // 3.2 需要更新 businessIds 对应的记录，只更新 nextTime
        if (CollUtil.isNotEmpty(createReqVO.getBusinessIds())) {
            businessService.updateBusinessContactNextTime(createReqVO.getBusinessIds(), createReqVO.getNextTime());
        }
        return record.getId();
    }

    @Override
    public void createFollowUpRecordBatch(List<CrmFollowUpCreateReqBO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        crmFollowUpRecordMapper.insertBatch(BeanUtils.toBean(list, CrmFollowUpRecordDO.class));
    }

    @Override
    public void deleteFollowUpRecord(Long id, Long userId) {
        // 校验存在
        CrmFollowUpRecordDO followUpRecord = validateFollowUpRecordExists(id);
        // 校验权限
        if (!permissionService.hasPermission(followUpRecord.getBizType(), followUpRecord.getBizId(), userId, CrmPermissionLevelEnum.OWNER)) {
            throw exception(FOLLOW_UP_RECORD_DELETE_DENIED);
        }

        // 删除
        crmFollowUpRecordMapper.deleteById(id);
        // 同步扣减业务对象的累计跟进次数，保证「次数」与跟进记录条数一致
        updateBizFollowUpCount(followUpRecord.getBizType(), followUpRecord.getBizId(), -1);
    }

    /**
     * 变更业务对象的累计跟进次数（客户 / 线索 / 商机）
     *
     * @param bizType 业务类型 {@link CrmBizTypeEnum}
     * @param bizId   业务编号
     * @param delta   变更量：新增跟进 +1，删除跟进 -1
     */
    private void updateBizFollowUpCount(Integer bizType, Long bizId, int delta) {
        if (ObjUtil.equal(CrmBizTypeEnum.CRM_CUSTOMER.getType(), bizType)) {
            customerMapper.updateFollowUpCountById(bizId, delta);
        } else if (ObjUtil.equal(CrmBizTypeEnum.CRM_BUSINESS.getType(), bizType)) {
            businessMapper.updateFollowUpCountById(bizId, delta);
        } else if (ObjUtil.equal(CrmBizTypeEnum.CRM_CLUE.getType(), bizType)) {
            clueMapper.updateFollowUpCountById(bizId, delta);
        }
    }

    @Override
    public void deleteFollowUpRecordByBiz(Integer bizType, Long bizId) {
        crmFollowUpRecordMapper.deleteByBiz(bizType, bizId);
    }

    private CrmFollowUpRecordDO validateFollowUpRecordExists(Long id) {
        CrmFollowUpRecordDO followUpRecord = crmFollowUpRecordMapper.selectById(id);
        if (followUpRecord == null) {
            throw exception(FOLLOW_UP_RECORD_NOT_EXISTS);
        }
        return followUpRecord;
    }

    @Override
    public CrmFollowUpRecordDO getFollowUpRecord(Long id) {
        return crmFollowUpRecordMapper.selectById(id);
    }

    @Override
    @CrmPermission(bizTypeValue = "#pageReqVO.bizType", bizId = "#pageReqVO.bizId", level = CrmPermissionLevelEnum.READ)
    public PageResult<CrmFollowUpRecordDO> getFollowUpRecordPage(CrmFollowUpRecordPageReqVO pageReqVO) {
        return crmFollowUpRecordMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CrmFollowUpRecordDO> getFollowUpRecordByBiz(Integer bizType, Collection<Long> bizIds) {
        return crmFollowUpRecordMapper.selectListByBiz(bizType, bizIds);
    }

}