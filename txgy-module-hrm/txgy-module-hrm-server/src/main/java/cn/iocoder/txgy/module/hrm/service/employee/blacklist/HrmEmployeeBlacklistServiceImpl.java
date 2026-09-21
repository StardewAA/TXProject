package cn.iocoder.txgy.module.hrm.service.employee.blacklist;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist.HrmEmployeeBlacklistSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.blacklist.HrmEmployeeBlacklistDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.blacklist.HrmEmployeeBlacklistMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_BLACKLIST_HIT;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_BLACKLIST_NOT_EXISTS;

/**
 * HRM 员工黑名单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmEmployeeBlacklistServiceImpl implements HrmEmployeeBlacklistService {

    /**
     * 状态：生效
     */
    private static final Integer STATUS_ACTIVE = 1;
    /**
     * 状态：已解除
     */
    private static final Integer STATUS_RELEASED = 2;

    @Resource
    private HrmEmployeeBlacklistMapper blacklistMapper;

    @Override
    public Long createBlacklist(HrmEmployeeBlacklistSaveReqVO createReqVO) {
        HrmEmployeeBlacklistDO blacklist = BeanUtils.toBean(createReqVO, HrmEmployeeBlacklistDO.class);
        if (blacklist.getJoinTime() == null) {
            blacklist.setJoinTime(LocalDateTime.now());
        }
        if (blacklist.getStatus() == null) {
            blacklist.setStatus(STATUS_ACTIVE);
        }
        if (blacklist.getPermanentFlag() == null) {
            blacklist.setPermanentFlag(false);
        }
        if (blacklist.getSource() == null) {
            blacklist.setSource(1);
        }
        blacklist.setId(null);
        blacklistMapper.insert(blacklist);
        return blacklist.getId();
    }

    @Override
    public void updateBlacklist(HrmEmployeeBlacklistSaveReqVO updateReqVO) {
        validateBlacklistExists(updateReqVO.getId());
        blacklistMapper.updateById(BeanUtils.toBean(updateReqVO, HrmEmployeeBlacklistDO.class));
    }

    @Override
    public void releaseBlacklist(Long id) {
        HrmEmployeeBlacklistDO blacklist = validateBlacklistExists(id);
        if (STATUS_RELEASED.equals(blacklist.getStatus())) {
            return;
        }
        blacklistMapper.updateById(new HrmEmployeeBlacklistDO().setId(id)
                .setStatus(STATUS_RELEASED).setReleaseTime(LocalDateTime.now()));
    }

    @Override
    public void deleteBlacklist(Long id) {
        validateBlacklistExists(id);
        blacklistMapper.deleteById(id);
    }

    @Override
    public HrmEmployeeBlacklistDO getBlacklist(Long id) {
        return blacklistMapper.selectById(id);
    }

    @Override
    public HrmEmployeeBlacklistDO validateBlacklistExists(Long id) {
        HrmEmployeeBlacklistDO blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) {
            throw exception(EMPLOYEE_BLACKLIST_NOT_EXISTS);
        }
        return blacklist;
    }

    @Override
    public PageResult<HrmEmployeeBlacklistDO> getBlacklistPage(HrmEmployeeBlacklistPageReqVO pageReqVO) {
        return blacklistMapper.selectPage(pageReqVO);
    }

    @Override
    public void validateNotBlacklisted(String name, String mobile, String idCardNo) {
        HrmEmployeeBlacklistDO hit = null;
        // 1. 优先按身份证号精确命中
        if (StrUtil.isNotBlank(idCardNo)) {
            hit = CollUtil.getFirst(blacklistMapper.selectActiveByIdCardNo(idCardNo));
        }
        // 2. 其次按手机号命中（黑名单登记时手机号必填）
        if (hit == null && StrUtil.isNotBlank(mobile)) {
            hit = CollUtil.getFirst(blacklistMapper.selectActiveByMobile(mobile));
            // 手机号相同但姓名也相同才算命中，避免号码复用误伤
            if (hit != null && StrUtil.isNotBlank(name) && StrUtil.isNotBlank(hit.getName())
                    && !StrUtil.equals(name, hit.getName())) {
                hit = null;
            }
        }
        if (hit != null) {
            throw exception(EMPLOYEE_BLACKLIST_HIT, StrUtil.blankToDefault(hit.getReason(), "未填写原因"));
        }
    }

    /**
     * 查询某员工的生效黑名单记录（供员工详情/离职校验使用）
     */
    public List<HrmEmployeeBlacklistDO> getActiveByEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        return blacklistMapper.selectActiveByEmployeeId(employeeId);
    }

}
