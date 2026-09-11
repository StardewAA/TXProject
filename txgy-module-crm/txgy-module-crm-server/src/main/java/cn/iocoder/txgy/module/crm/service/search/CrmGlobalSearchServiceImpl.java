package cn.iocoder.txgy.module.crm.service.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.collection.CollectionUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.search.vo.CrmGlobalSearchRespVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.mysql.business.CrmBusinessMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.contact.CrmContactMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.contract.CrmContractMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CRM 全局检索 Service 实现类
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmGlobalSearchServiceImpl implements CrmGlobalSearchService {

    private static final int DEFAULT_LIMIT = 5;

    private static final int MAX_LIMIT = 20;

    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmClueMapper clueMapper;
    @Resource
    private CrmContactMapper contactMapper;
    @Resource
    private CrmBusinessMapper businessMapper;
    @Resource
    private CrmContractMapper contractMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public CrmGlobalSearchRespVO searchAll(String keyword, Integer limit) {
        CrmGlobalSearchRespVO resp = new CrmGlobalSearchRespVO();
        if (StrUtil.isBlank(keyword)) {
            return emptyResult(resp);
        }
        String kw = keyword.trim();
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        // 1. 客户（含上游供应商）：名称 / 手机 / 电话
        LambdaQueryWrapper<CrmCustomerDO> customerQuery = new LambdaQueryWrapperX<CrmCustomerDO>()
                .and(w -> w.like(CrmCustomerDO::getName, kw)
                        .or().like(CrmCustomerDO::getMobile, kw)
                        .or().like(CrmCustomerDO::getTelephone, kw));
        customerQuery.last("LIMIT " + size);
        List<CrmCustomerDO> customers = customerMapper.selectList(customerQuery);

        // 2. 线索：名称 / 手机 / 电话
        LambdaQueryWrapper<CrmClueDO> clueQuery = new LambdaQueryWrapperX<CrmClueDO>()
                .and(w -> w.like(CrmClueDO::getName, kw)
                        .or().like(CrmClueDO::getMobile, kw)
                        .or().like(CrmClueDO::getTelephone, kw));
        clueQuery.last("LIMIT " + size);
        List<CrmClueDO> clues = clueMapper.selectList(clueQuery);

        // 3. 联系人：名称 / 手机 / 电话
        LambdaQueryWrapper<CrmContactDO> contactQuery = new LambdaQueryWrapperX<CrmContactDO>()
                .and(w -> w.like(CrmContactDO::getName, kw)
                        .or().like(CrmContactDO::getMobile, kw)
                        .or().like(CrmContactDO::getTelephone, kw));
        contactQuery.last("LIMIT " + size);
        List<CrmContactDO> contacts = contactMapper.selectList(contactQuery);

        // 4. 商机：名称
        LambdaQueryWrapper<CrmBusinessDO> businessQuery = new LambdaQueryWrapperX<CrmBusinessDO>()
                .like(CrmBusinessDO::getName, kw);
        businessQuery.last("LIMIT " + size);
        List<CrmBusinessDO> businesses = businessMapper.selectList(businessQuery);

        // 5. 合同：名称 / 编号
        LambdaQueryWrapper<CrmContractDO> contractQuery = new LambdaQueryWrapperX<CrmContractDO>()
                .and(w -> w.like(CrmContractDO::getName, kw).or().like(CrmContractDO::getNo, kw));
        contractQuery.last("LIMIT " + size);
        List<CrmContractDO> contracts = contractMapper.selectList(contractQuery);

        // 6. 填充负责人姓名
        Map<Long, AdminUserRespDTO> userMap = getUserMap(customers, clues, contacts, businesses, contracts);

        resp.setCustomers(CollectionUtils.convertList(customers, item -> CrmGlobalSearchRespVO.Item.builder()
                .id(item.getId()).name(item.getName()).bizType("customer").bizTypeName("客户")
                .mobile(item.getMobile()).telephone(item.getTelephone())
                .extra(item.getCustomerType() != null && item.getCustomerType() == 2 ? "上游供应商" : "下游客户")
                .ownerUserId(item.getOwnerUserId()).ownerUserName(getNickname(userMap, item.getOwnerUserId()))
                .build()));
        resp.setClues(CollectionUtils.convertList(clues, item -> CrmGlobalSearchRespVO.Item.builder()
                .id(item.getId()).name(item.getName()).bizType("clue").bizTypeName("线索")
                .mobile(item.getMobile()).telephone(item.getTelephone())
                .extra(Boolean.TRUE.equals(item.getTransformStatus()) ? "已转化" : "未转化")
                .ownerUserId(item.getOwnerUserId()).ownerUserName(getNickname(userMap, item.getOwnerUserId()))
                .build()));
        resp.setContacts(CollectionUtils.convertList(contacts, item -> CrmGlobalSearchRespVO.Item.builder()
                .id(item.getId()).name(item.getName()).bizType("contact").bizTypeName("联系人")
                .mobile(item.getMobile()).telephone(item.getTelephone()).extra(item.getPost())
                .ownerUserId(item.getOwnerUserId()).ownerUserName(getNickname(userMap, item.getOwnerUserId()))
                .build()));
        resp.setBusinesses(CollectionUtils.convertList(businesses, item -> CrmGlobalSearchRespVO.Item.builder()
                .id(item.getId()).name(item.getName()).bizType("business").bizTypeName("商机")
                .ownerUserId(item.getOwnerUserId()).ownerUserName(getNickname(userMap, item.getOwnerUserId()))
                .build()));
        resp.setContracts(CollectionUtils.convertList(contracts, item -> CrmGlobalSearchRespVO.Item.builder()
                .id(item.getId()).name(item.getName()).bizType("contract").bizTypeName("合同")
                .extra(item.getNo())
                .ownerUserId(item.getOwnerUserId()).ownerUserName(getNickname(userMap, item.getOwnerUserId()))
                .build()));
        resp.setTotal(resp.getCustomers().size() + resp.getClues().size() + resp.getContacts().size()
                + resp.getBusinesses().size() + resp.getContracts().size());
        return resp;
    }

    private CrmGlobalSearchRespVO emptyResult(CrmGlobalSearchRespVO resp) {
        resp.setCustomers(Collections.emptyList());
        resp.setClues(Collections.emptyList());
        resp.setContacts(Collections.emptyList());
        resp.setBusinesses(Collections.emptyList());
        resp.setContracts(Collections.emptyList());
        resp.setTotal(0);
        return resp;
    }

    private Map<Long, AdminUserRespDTO> getUserMap(List<CrmCustomerDO> customers, List<CrmClueDO> clues,
                                                   List<CrmContactDO> contacts, List<CrmBusinessDO> businesses,
                                                   List<CrmContractDO> contracts) {
        List<Long> ownerIds = new ArrayList<>();
        ownerIds.addAll(CollectionUtils.convertList(customers, CrmCustomerDO::getOwnerUserId));
        ownerIds.addAll(CollectionUtils.convertList(clues, CrmClueDO::getOwnerUserId));
        ownerIds.addAll(CollectionUtils.convertList(contacts, CrmContactDO::getOwnerUserId));
        ownerIds.addAll(CollectionUtils.convertList(businesses, CrmBusinessDO::getOwnerUserId));
        ownerIds.addAll(CollectionUtils.convertList(contracts, CrmContractDO::getOwnerUserId));
        ownerIds.removeIf(Objects::isNull);
        if (CollUtil.isEmpty(ownerIds)) {
            return Collections.emptyMap();
        }
        return adminUserApi.getUserMap(ownerIds);
    }

    private String getNickname(Map<Long, AdminUserRespDTO> userMap, Long userId) {
        if (userId == null || CollUtil.isEmpty(userMap)) {
            return null;
        }
        AdminUserRespDTO user = userMap.get(userId);
        return user == null ? null : user.getNickname();
    }

}
