package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 平台授权与经办用户管理（G 轨）：需求“管理端显示已授权投标人并支持管理用户”；
// 授权≠完善：列表行带档案完善度，供管理端识别“仅授权未完善”主体（此类主体不能投标/发布，但关注等照常）。

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserBindReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectUserDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectAuthMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectUserMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.SubjectAuthStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.SubjectTypeEnum;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 主体授权与经办用户服务实现（G 轨数据手动带租户条件；档案 T 轨跨租户读取用 TenantUtils.executeIgnore）。
 */
@Slf4j
@Service
public class SubjectAuthServiceImpl implements SubjectAuthService {

    private static final String ROLE_OPERATOR = "OPERATOR";

    @Resource
    private SubjectAuthMapper subjectAuthMapper;
    @Resource
    private SubjectProfileMapper subjectProfileMapper;
    @Resource
    private SubjectUserMapper subjectUserMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public PageResult<SubjectAuthRespVO> getAuthPage(SubjectAuthPageReqVO reqVO) {
        LambdaQueryWrapper<SubjectAuthDO> wrapper = new LambdaQueryWrapper<>();
        if (reqVO.getTenantId() != null) {
            wrapper.eq(SubjectAuthDO::getTenantId, reqVO.getTenantId());
        }
        if (StringUtils.hasText(reqVO.getSubjectType())) {
            wrapper.eq(SubjectAuthDO::getSubjectType, reqVO.getSubjectType());
        }
        if (StringUtils.hasText(reqVO.getAuthStatus())) {
            wrapper.eq(SubjectAuthDO::getAuthStatus, reqVO.getAuthStatus());
        }
        if (StringUtils.hasText(reqVO.getTenantName())) {
            wrapper.like(SubjectAuthDO::getTenantName, reqVO.getTenantName());
        }
        wrapper.orderByDesc(SubjectAuthDO::getId);
        IPage<SubjectAuthDO> page = subjectAuthMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        List<SubjectAuthRespVO> rows = new ArrayList<>();
        for (SubjectAuthDO auth : page.getRecords()) {
            SubjectAuthRespVO vo = toAuthRespVO(auth);
            // 行内补档案完善度（跨租户 T 轨只读，executeIgnore + 显式租户条件）
            SubjectProfileDO profile = readProfile(auth.getTenantId(), auth.getSubjectType());
            vo.setProfileExists(profile != null);
            SubjectCompleteness.Assessment assessment = SubjectCompleteness.assess(profile);
            vo.setProfileComplete(assessment.complete);
            vo.setMissingRequiredCodes(assessment.complete ? new ArrayList<>() : assessment.missingCodes);
            vo.setMissingRequiredNames(assessment.complete ? new ArrayList<>() : assessment.missingNames);
            rows.add(vo);
        }
        return new PageResult<>(rows, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long authorize(SubjectAuthSaveReqVO reqVO) {
        if (!SubjectTypeEnum.isSupported(reqVO.getSubjectType())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_TYPE_INVALID);
        }
        SubjectAuthDO exists = findAuth(reqVO.getTenantId(), reqVO.getSubjectType());
        if (exists != null) {
            if (SubjectAuthStatusEnum.ENABLED.name().equals(exists.getAuthStatus())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_ALREADY_AUTHORIZED);
            }
            // 历史停用 → 重新启用（保留原行，更新状态/时间/操作人）
            exists.setAuthStatus(SubjectAuthStatusEnum.ENABLED.name());
            exists.setAuthorizeTime(LocalDateTime.now());
            exists.setDisableTime(null);
            exists.setAuthorizeUserId(getLoginUserId());
            if (StringUtils.hasText(reqVO.getTenantName())) {
                exists.setTenantName(reqVO.getTenantName());
            }
            if (reqVO.getRemark() != null) {
                exists.setRemark(reqVO.getRemark());
            }
            subjectAuthMapper.updateById(exists);
            return exists.getId();
        }
        SubjectAuthDO auth = new SubjectAuthDO();
        auth.setTenantId(reqVO.getTenantId());
        auth.setTenantName(reqVO.getTenantName());
        auth.setSubjectType(reqVO.getSubjectType());
        auth.setAuthStatus(SubjectAuthStatusEnum.ENABLED.name());
        auth.setAuthorizeUserId(getLoginUserId());
        auth.setAuthorizeTime(LocalDateTime.now());
        auth.setRemark(reqVO.getRemark());
        subjectAuthMapper.insert(auth);
        log.info("[authorize][tenantId={} subjectType={}] 平台授权主体（授权≠完善：档案完善后方可投标/发布）", auth.getTenantId(), auth.getSubjectType());
        return auth.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAuthStatus(Long id, String authStatus) {
        SubjectAuthDO auth = subjectAuthMapper.selectById(id);
        if (auth == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_AUTH_NOT_EXISTS);
        }
        SubjectAuthStatusEnum status = SubjectAuthStatusEnum.of(authStatus);
        if (status == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PAYLOAD_INVALID);
        }
        auth.setAuthStatus(status.name());
        if (status == SubjectAuthStatusEnum.ENABLED) {
            auth.setAuthorizeTime(LocalDateTime.now());
            auth.setDisableTime(null);
            auth.setAuthorizeUserId(getLoginUserId());
        } else {
            auth.setDisableTime(LocalDateTime.now());
        }
        subjectAuthMapper.updateById(auth);
    }

    @Override
    public PageResult<SubjectUserRespVO> getBindUserPage(SubjectUserPageReqVO reqVO) {
        LambdaQueryWrapper<SubjectUserDO> wrapper = new LambdaQueryWrapper<>();
        if (reqVO.getTenantId() != null) {
            wrapper.eq(SubjectUserDO::getTenantId, reqVO.getTenantId());
        }
        if (StringUtils.hasText(reqVO.getSubjectType())) {
            wrapper.eq(SubjectUserDO::getSubjectType, reqVO.getSubjectType());
        }
        if (StringUtils.hasText(reqVO.getUserRole())) {
            wrapper.eq(SubjectUserDO::getUserRole, reqVO.getUserRole());
        }
        if (reqVO.getEnabled() != null) {
            wrapper.eq(SubjectUserDO::getEnabled, reqVO.getEnabled());
        }
        wrapper.orderByDesc(SubjectUserDO::getId);
        IPage<SubjectUserDO> page = subjectUserMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toUserRespVO).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long bindUser(SubjectUserBindReqVO reqVO) {
        if (!SubjectTypeEnum.isSupported(reqVO.getSubjectType())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_TYPE_INVALID);
        }
        SubjectUserDO exists = subjectUserMapper.selectOne(new LambdaQueryWrapper<SubjectUserDO>()
                .eq(SubjectUserDO::getTenantId, reqVO.getTenantId())
                .eq(SubjectUserDO::getSubjectType, reqVO.getSubjectType())
                .eq(SubjectUserDO::getUserId, reqVO.getUserId())
                .last("LIMIT 1"));
        if (exists != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_USER_ALREADY_BOUND);
        }
        // 用户有效性校验 + 昵称快照（RPC system AdminUserApi；用户启用状态由 validate 保证）
        String nickname = null;
        boolean rpcOk = true;
        boolean valid = false;
        try {
            Boolean ok = adminUserApi.validateUserList(java.util.Collections.singleton(reqVO.getUserId())).getCheckedData();
            valid = Boolean.TRUE.equals(ok);
            if (valid) {
                AdminUserRespDTO user = adminUserApi.getUser(reqVO.getUserId()).getCheckedData();
                nickname = user != null ? user.getNickname() : null;
            }
        } catch (Exception e) {
            // RPC 异常（网络/超时等）≠“用户无效”：降级为宽松绑定并告警，避免跨系统抖动阻断绑定流程
            rpcOk = false;
            log.warn("[bindUser][userId={}] AdminUserApi 调用异常，降级宽松绑定（归属租户强校验为 system 侧扩展位）", reqVO.getUserId(), e);
        }
        if (rpcOk && !valid) {
            // RPC 明确返回用户无效（不存在/禁用）→ 拒绝绑定
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_USER_INVALID);
        }
        if (nickname == null) {
            log.warn("[bindUser][userId={}] 未取得用户昵称快照（无效或 RPC 异常），绑定记录 nickname 置空", reqVO.getUserId());
        }
        SubjectUserDO bind = new SubjectUserDO();
        bind.setTenantId(reqVO.getTenantId());
        bind.setSubjectType(reqVO.getSubjectType());
        bind.setUserId(reqVO.getUserId());
        bind.setUserNickname(nickname);
        bind.setUserRole(StringUtils.hasText(reqVO.getUserRole()) ? reqVO.getUserRole() : ROLE_OPERATOR);
        bind.setEnabled(Boolean.TRUE);
        bind.setRemark(reqVO.getRemark());
        subjectUserMapper.insert(bind);
        return bind.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindUser(Long id) {
        SubjectUserDO bind = id == null ? null : subjectUserMapper.selectById(id);
        if (bind == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_USER_NOT_EXISTS);
        }
        subjectUserMapper.deleteById(bind.getId());
    }

    // ========== 私有 ==========

    private SubjectAuthDO findAuth(Long tenantId, String subjectType) {
        return subjectAuthMapper.selectOne(new LambdaQueryWrapper<SubjectAuthDO>()
                .eq(SubjectAuthDO::getTenantId, tenantId)
                .eq(SubjectAuthDO::getSubjectType, subjectType)
                .last("LIMIT 1"));
    }

    /** 跨租户读 T 轨档案（executeIgnore + 显式租户条件） */
    private SubjectProfileDO readProfile(Long tenantId, String subjectType) {
        return TenantUtils.executeIgnore(() -> subjectProfileMapper.selectOne(new LambdaQueryWrapper<SubjectProfileDO>()
                .eq(SubjectProfileDO::getTenantId, tenantId)
                .eq(SubjectProfileDO::getSubjectType, subjectType)
                .last("LIMIT 1")));
    }

    private SubjectAuthRespVO toAuthRespVO(SubjectAuthDO auth) {
        SubjectAuthRespVO vo = new SubjectAuthRespVO();
        vo.setId(auth.getId());
        vo.setTenantId(auth.getTenantId());
        vo.setTenantName(auth.getTenantName());
        vo.setSubjectType(auth.getSubjectType());
        vo.setAuthStatus(auth.getAuthStatus());
        vo.setAuthorizeTime(auth.getAuthorizeTime());
        vo.setDisableTime(auth.getDisableTime());
        vo.setRemark(auth.getRemark());
        vo.setCreateTime(auth.getCreateTime());
        return vo;
    }

    private SubjectUserRespVO toUserRespVO(SubjectUserDO bind) {
        SubjectUserRespVO vo = new SubjectUserRespVO();
        vo.setId(bind.getId());
        vo.setTenantId(bind.getTenantId());
        vo.setSubjectType(bind.getSubjectType());
        vo.setUserId(bind.getUserId());
        vo.setUserNickname(bind.getUserNickname());
        vo.setUserRole(bind.getUserRole());
        vo.setEnabled(bind.getEnabled());
        vo.setRemark(bind.getRemark());
        vo.setCreateTime(bind.getCreateTime());
        return vo;
    }
}
