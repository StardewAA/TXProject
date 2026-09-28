package cn.iocoder.txgy.module.bidding.service.subject;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanySaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectCompanyDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectCompanyMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 所属企业 Service 实现（T 轨；bidding_subject_company）。
 *
 * <p>业务口径（用户 2026-09-18）：「所属企业」不是租户——租户 id 在超管的「全局租户选择」里属平台级；
 * 本实体是**租户自己维护的企业名录**（本企业 SELF + 子公司/分公司），用于把证照/资质按"登记在哪家公司
 * 名下"归集。租户隔离由框架 TenantDatabaseInterceptor 自动实施，本类不手写 tenant_id 条件。
 *
 * <p>规范出处：《招投标模块开发规范》§4.5（乐观锁 CAS、幂等判重）、§5 五（T 轨）；
 * USCC 校验复用主体档案既有校验器（GB 32100-2015，UsccUtils）。
 */
@Service
public class SubjectCompanyServiceImpl implements SubjectCompanyService {

    /** 本企业（每租户仅一条） */
    private static final String SELF = "SELF";

    @Resource
    private SubjectCompanyMapper subjectCompanyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSubjectCompany(SubjectCompanySaveReqVO reqVO) {
        // USCC 强校验（GB 32100-2015 格式 + 校验码；复用主体档案既有校验器）
        if (StringUtils.hasText(reqVO.getUscc()) && !UsccUtils.isValid(reqVO.getUscc())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_CREDIT_CODE_INVALID);
        }
        // 本企业 SELF 每租户仅一条
        if (SELF.equals(reqVO.getCompanyType()) && subjectCompanyMapper.selectByCompanyType(SELF) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_SELF_DUPLICATE);
        }
        // 判重：USCC 有值按 USCC（同租户唯一）；USCC 为空按名称判重
        checkDuplicateForCreate(reqVO);
        SubjectCompanyDO company = BeanUtils.toBean(reqVO, SubjectCompanyDO.class);
        Long parentId = reqVO.getParentId() == null ? 0L : reqVO.getParentId();
        company.setParentId(parentId);
        if (parentId != 0L) {
            resolveParent(company, parentId, null);
        }
        if (company.getEnabled() == null) {
            company.setEnabled(Boolean.TRUE);
        }
        subjectCompanyMapper.insert(company);
        return company.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSubjectCompany(SubjectCompanySaveReqVO reqVO) {
        SubjectCompanyDO db = subjectCompanyMapper.selectById(reqVO.getId());
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_NOT_EXISTS);
        }
        ensureVersionMatch(reqVO.getVersion(), db);
        if (StringUtils.hasText(reqVO.getUscc()) && !UsccUtils.isValid(reqVO.getUscc())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_CREDIT_CODE_INVALID);
        }
        // 改为 SELF 时校验唯一
        if (SELF.equals(reqVO.getCompanyType()) && !SELF.equals(db.getCompanyType())) {
            SubjectCompanyDO exist = subjectCompanyMapper.selectByCompanyType(SELF);
            if (exist != null && !exist.getId().equals(db.getId())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_SELF_DUPLICATE);
            }
        }
        checkDuplicateForUpdate(reqVO, db);
        Long parentId = reqVO.getParentId() == null ? 0L : reqVO.getParentId();
        if (parentId.equals(db.getId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_PARENT_INVALID);
        }
        if (parentId != 0L) {
            resolveParent(db, parentId, db.getId());
        } else {
            db.setParentName(null);
        }
        // 应用可编辑字段
        db.setCompanyName(reqVO.getCompanyName());
        db.setUscc(reqVO.getUscc());
        db.setCompanyType(reqVO.getCompanyType());
        db.setParentId(parentId);
        db.setLegalPersonName(reqVO.getLegalPersonName());
        db.setContactName(reqVO.getContactName());
        db.setContactPhone(reqVO.getContactPhone());
        db.setRegionCode(reqVO.getRegionCode());
        db.setIndustryCode(reqVO.getIndustryCode());
        db.setRemark(reqVO.getRemark());
        if (reqVO.getEnabled() != null) {
            db.setEnabled(reqVO.getEnabled());
        }
        int rows = subjectCompanyMapper.updateById(db);
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSubjectCompanyStatus(Long id, Boolean enabled) {
        SubjectCompanyDO db = subjectCompanyMapper.selectById(id);
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_NOT_EXISTS);
        }
        db.setEnabled(enabled == null ? Boolean.FALSE : enabled);
        subjectCompanyMapper.updateById(db);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSubjectCompany(Long id) {
        // TODO 优先级2后续：证书/资质挂 company_id 后，此处校验"被引用则拒删、提示改用停用"
        SubjectCompanyDO db = subjectCompanyMapper.selectById(id);
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_NOT_EXISTS);
        }
        subjectCompanyMapper.deleteById(id);
    }

    @Override
    public SubjectCompanyRespVO getSubjectCompany(Long id) {
        SubjectCompanyDO db = subjectCompanyMapper.selectById(id);
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_NOT_EXISTS);
        }
        return toRespVO(db);
    }

    @Override
    public PageResult<SubjectCompanyRespVO> getSubjectCompanyPage(SubjectCompanyPageReqVO reqVO) {
        LambdaQueryWrapper<SubjectCompanyDO> query = new LambdaQueryWrapper<>();
        query.like(StringUtils.hasText(reqVO.getCompanyName()), SubjectCompanyDO::getCompanyName, reqVO.getCompanyName());
        query.eq(StringUtils.hasText(reqVO.getUscc()), SubjectCompanyDO::getUscc, reqVO.getUscc());
        query.eq(StringUtils.hasText(reqVO.getCompanyType()), SubjectCompanyDO::getCompanyType, reqVO.getCompanyType());
        query.eq(reqVO.getParentId() != null, SubjectCompanyDO::getParentId, reqVO.getParentId());
        query.eq(reqVO.getEnabled() != null, SubjectCompanyDO::getEnabled, reqVO.getEnabled());
        query.orderByDesc(SubjectCompanyDO::getId);
        Page<SubjectCompanyDO> page = subjectCompanyMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    // ========== 内部工具 ==========

    /** 上级校验 + 父级名称快照 + 防环（selfId 为空=新增） */
    private void resolveParent(SubjectCompanyDO company, Long parentId, Long selfId) {
        SubjectCompanyDO parent = subjectCompanyMapper.selectById(parentId);
        if (parent == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_PARENT_INVALID);
        }
        company.setParentName(parent.getCompanyName());
        checkNoCycle(selfId, parentId);
    }

    /** 沿 parentId 向上走，若回到自身即成环（A→B→A）。 */
    private void checkNoCycle(Long selfId, Long parentId) {
        if (selfId == null) {
            return; // 新增无自身 id，不可能成环
        }
        Long cur = parentId;
        Set<Long> seen = new HashSet<>();
        while (cur != null && cur != 0L) {
            if (cur.equals(selfId)) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_PARENT_INVALID);
            }
            if (!seen.add(cur)) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_PARENT_INVALID);
            }
            SubjectCompanyDO node = subjectCompanyMapper.selectById(cur);
            if (node == null) {
                break;
            }
            cur = node.getParentId() == null ? 0L : node.getParentId();
        }
    }

    private void checkDuplicateForCreate(SubjectCompanySaveReqVO reqVO) {
        if (StringUtils.hasText(reqVO.getUscc())) {
            if (subjectCompanyMapper.selectByUscc(reqVO.getUscc()) != null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_USCC_DUPLICATE);
            }
        } else if (subjectCompanyMapper.selectByCompanyName(reqVO.getCompanyName()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_NAME_DUPLICATE);
        }
    }

    private void checkDuplicateForUpdate(SubjectCompanySaveReqVO reqVO, SubjectCompanyDO db) {
        if (StringUtils.hasText(reqVO.getUscc())) {
            SubjectCompanyDO exist = subjectCompanyMapper.selectByUscc(reqVO.getUscc());
            if (exist != null && !exist.getId().equals(db.getId())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_USCC_DUPLICATE);
            }
        } else {
            SubjectCompanyDO exist = subjectCompanyMapper.selectByCompanyName(reqVO.getCompanyName());
            if (exist != null && !exist.getId().equals(db.getId())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_NAME_DUPLICATE);
            }
        }
    }

    private void ensureVersionMatch(Integer reqVersion, SubjectCompanyDO db) {
        if (reqVersion != null && !reqVersion.equals(db.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_COMPANY_DATA_MODIFIED);
        }
    }

    private SubjectCompanyRespVO toRespVO(SubjectCompanyDO company) {
        SubjectCompanyRespVO resp = new SubjectCompanyRespVO();
        resp.setId(company.getId());
        resp.setTenantId(company.getTenantId());
        resp.setCompanyName(company.getCompanyName());
        resp.setUscc(company.getUscc());
        resp.setCompanyType(company.getCompanyType());
        resp.setParentId(company.getParentId());
        resp.setParentName(company.getParentName());
        resp.setLegalPersonName(company.getLegalPersonName());
        resp.setContactName(company.getContactName());
        resp.setContactPhone(company.getContactPhone());
        resp.setRegionCode(company.getRegionCode());
        resp.setIndustryCode(company.getIndustryCode());
        resp.setRemark(company.getRemark());
        resp.setEnabled(company.getEnabled());
        resp.setVersion(company.getVersion());
        resp.setCreateTime(company.getCreateTime());
        resp.setUpdateTime(company.getUpdateTime());
        return resp;
    }
}
