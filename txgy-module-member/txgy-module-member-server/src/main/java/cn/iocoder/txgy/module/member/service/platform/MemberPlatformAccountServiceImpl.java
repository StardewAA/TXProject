package cn.iocoder.txgy.module.member.service.platform;

import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformTenantMembershipRespDTO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformTenantMembershipDO;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAccountMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAddressMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformTenantMembershipMapper;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_NOT_READY;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ADDRESS_NOT_FOUND;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_IDENTITY_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;

@Service
@Validated
public class MemberPlatformAccountServiceImpl implements MemberPlatformAccountService {

    @Resource
    private MemberPlatformAccountMapper platformAccountMapper;
    @Resource
    private MemberPlatformTenantMembershipMapper membershipMapper;
    @Resource
    private MemberPlatformAddressMapper platformAddressMapper;

    @Override
    public MemberPlatformAccountRespDTO resolveRequired(Long legacyMemberId, Long sourceTenantId) {
        if (legacyMemberId == null || legacyMemberId <= 0 || sourceTenantId == null || sourceTenantId <= 0
                || sourceTenantId == 3000L) {
            throw exception(PLATFORM_ACCOUNT_NOT_READY);
        }
        MemberPlatformAccountDO account = platformAccountMapper.selectReadyByLegacySession(
                legacyMemberId, sourceTenantId);
        if (account == null) {
            throw exception(PLATFORM_ACCOUNT_NOT_READY);
        }
        // Multiple tenant-member rows may share one platform account only after an explicit
        // identity review. Equal login identifiers or password hashes are not identity proof.
        if (platformAccountMapper.countUnsafeReadyIdentityGraph(account.getId()) != 0) {
            throw exception(PLATFORM_ACCOUNT_NOT_READY);
        }
        List<MemberPlatformTenantMembershipRespDTO> memberships = membershipMapper
                .selectActiveByPlatformAccountId(account.getId()).stream()
                .map(MemberPlatformAccountServiceImpl::convertMembership)
                .toList();
        return new MemberPlatformAccountRespDTO()
                .setPlatformAccountId(account.getId())
                .setPlatformNo(account.getPlatformNo())
                .setCanonicalMemberId(account.getCanonicalMemberId())
                .setLegacyMemberId(legacyMemberId)
                .setSourceTenantId(sourceTenantId)
                .setMemberships(memberships);
    }

    @Override
    public MemberPlatformAddressRespDTO resolveAddressRequired(Long legacyMemberId, Long sourceTenantId,
                                                               Long addressId) {
        MemberPlatformAccountRespDTO account = resolveRequired(legacyMemberId, sourceTenantId);
        if (addressId == null || addressId <= 0) {
            throw exception(PLATFORM_ADDRESS_NOT_FOUND);
        }
        MemberPlatformAddressRespDTO address = platformAddressMapper.selectLinkedAddress(
                account.getPlatformAccountId(), legacyMemberId, sourceTenantId, addressId);
        if (address == null) {
            throw exception(PLATFORM_ADDRESS_NOT_FOUND);
        }
        return address;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public MemberPlatformAccountRespDTO createForRegistration(MemberUserDO member, Long sourceTenantId) {
        if (member == null || member.getId() == null || member.getId() <= 0 || sourceTenantId == null
                || sourceTenantId <= 0 || sourceTenantId == 3000L) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
        }
        List<Credential> credentials = registrationCredentials(member);
        Credential credential = credentials.get(0);
        MemberPlatformAccountDO existing = platformAccountMapper.selectReadyRegistrationLink(
                member.getId(), sourceTenantId);
        if (existing != null) {
            if (!Objects.equals(existing.getCredentialType(), credential.type())
                    || !Objects.equals(existing.getCredentialIdentifier(), credential.identifier())
                    || !Objects.equals(existing.getLoginName(), credentialIdentifier(credentials, "LOGIN_NAME"))
                    || !Objects.equals(existing.getMobile(), credentialIdentifier(credentials, "MOBILE"))) {
                throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
            }
            requireUniqueRegistrationIdentifiers(credentials);
            return accountResponse(existing, member.getId(), sourceTenantId);
        }

        // The just-created member must be the only non-deleted owner of every usable identifier.
        // Independent global login-name and mobile keys below close both concurrent races.
        requireUniqueRegistrationIdentifiers(credentials);
        MemberPlatformAccountDO account = MemberPlatformAccountDO.builder()
                .platformNo(platformNo(member.getId()))
                .credentialType(credential.type())
                .credentialIdentifier(credential.identifier())
                .loginName(credentialIdentifier(credentials, "LOGIN_NAME"))
                .mobile(credentialIdentifier(credentials, "MOBILE"))
                .canonicalMemberId(member.getId())
                .status(0)
                .build();
        if (platformAccountMapper.insertRegistrationAccount(account) != 1 || account.getId() == null
                || platformAccountMapper.insertRegistrationLink(account.getId(), member.getId(), sourceTenantId) != 1) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
        }
        return accountResponse(account, member.getId(), sourceTenantId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void synchronizeMobileAlias(MemberUserDO memberBeforeUpdate, String newMobile) {
        if (memberBeforeUpdate == null || memberBeforeUpdate.getId() == null
                || memberBeforeUpdate.getTenantId() == null || memberBeforeUpdate.getTenantId() == 3000L) {
            return;
        }
        String oldMobile = normalizedMobile(memberBeforeUpdate.getMobile());
        String normalizedNewMobile = normalizedMobile(newMobile);
        if (Objects.equals(oldMobile, normalizedNewMobile)) {
            return;
        }
        // Keep the global member-identifier check ahead of link discovery. Besides rejecting a
        // conflict with less work, this preserves the fail-closed rule for legacy members that
        // have not entered the platform-account domain yet.
        if (normalizedNewMobile != null && platformAccountMapper.countOtherMembersByMobile(normalizedNewMobile,
                memberBeforeUpdate.getId(), memberBeforeUpdate.getTenantId()) != 0L) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
        }
        long linkCount = platformAccountMapper.countActiveLinksByLegacyMember(
                memberBeforeUpdate.getId(), memberBeforeUpdate.getTenantId());
        if (linkCount == 0L) {
            if (normalizedNewMobile == null) {
                return; // 未加入平台账号域的旧会员仍沿用原有后台资料规则。
            }
            // Even a legacy member without a platform link must not take an identifier already owned elsewhere:
            // doing so would make the existing platform login fail its global-uniqueness check.
            if (platformAccountMapper.countOtherPlatformAccountsByMobile(-1L, normalizedNewMobile) != 0L) {
                throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
            }
            return; // Legacy member has not joined the platform-account domain yet.
        }
        if (linkCount != 1L) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
        }
        // 已关联的平台账号必须始终保留可验证的身份手机号。清空会让平台别名与
        // member_user 分离，且后续换绑无法再通过旧值条件更新，因此必须 fail-closed。
        if (normalizedNewMobile == null) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
        }
        MemberPlatformAccountDO account = platformAccountMapper.selectReadyRegistrationLink(
                memberBeforeUpdate.getId(), memberBeforeUpdate.getTenantId());
        if (account == null || account.getId() == null
                || !Objects.equals(account.getCanonicalMemberId(), memberBeforeUpdate.getId())
                || platformAccountMapper.countReadyLinksByPlatformAccount(account.getId()) != 1L
                || !Objects.equals(normalizedMobile(account.getMobile()), oldMobile)
                || (Objects.equals(account.getCredentialType(), "MOBILE")
                    && !Objects.equals(account.getCredentialIdentifier(), oldMobile))) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
        }
        if (platformAccountMapper.countOtherPlatformAccountsByMobile(account.getId(), normalizedNewMobile) != 0L) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
        }
        try {
            if (platformAccountMapper.updateMobileAlias(account.getId(), oldMobile, normalizedNewMobile) != 1) {
                throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
            }
        } catch (DataIntegrityViolationException ex) {
            // The global unique key is the final guard for concurrent alias changes.
            throw exception(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT);
        }
    }

    @Override
    public MemberPlatformLoginCandidate resolveLoginRequired(String account) {
        Credential credential = loginCredential(account);
        List<MemberPlatformLoginCandidate> candidates = platformAccountMapper.selectLoginCandidates(
                credential.type(), credential.identifier());
        if (candidates.size() != 1) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        MemberPlatformLoginCandidate candidate = candidates.get(0);
        long memberCount = platformAccountMapper.countMembersByCredential(credential.type(), credential.identifier());
        long readyCount = platformAccountMapper.countReadyMembersByCredential(candidate.getPlatformAccountId(),
                credential.type(), credential.identifier());
        long hashCount = platformAccountMapper.countReadyCredentialHashes(candidate.getPlatformAccountId(),
                credential.type(), credential.identifier());
        if (memberCount <= 0 || memberCount != readyCount || hashCount != 1L
                || platformAccountMapper.countUnsafeReadyIdentityGraph(candidate.getPlatformAccountId()) != 0
                || candidate.getCanonicalMemberId() == null || candidate.getCanonicalMemberId() <= 0
                || candidate.getSourceTenantId() == null || candidate.getSourceTenantId() <= 0
                || candidate.getSourceTenantId() == 3000L
                || !Objects.equals(candidate.getCredentialIdentifier(), credential.identifier())
                || !Objects.equals(candidate.getCredentialType(), credential.type())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        return candidate;
    }

    private MemberPlatformAccountRespDTO accountResponse(MemberPlatformAccountDO account, Long legacyMemberId,
                                                          Long sourceTenantId) {
        return new MemberPlatformAccountRespDTO()
                .setPlatformAccountId(account.getId())
                .setPlatformNo(account.getPlatformNo())
                .setCanonicalMemberId(account.getCanonicalMemberId())
                .setLegacyMemberId(legacyMemberId)
                .setSourceTenantId(sourceTenantId)
                .setMemberships(List.of());
    }

    private void requireUniqueRegistrationIdentifiers(List<Credential> credentials) {
        if (credentials.stream().anyMatch(credential -> platformAccountMapper.countMembersByCredential(
                credential.type(), credential.identifier()) != 1L)) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
        }
    }

    private static List<Credential> registrationCredentials(MemberUserDO member) {
        java.util.ArrayList<Credential> credentials = new java.util.ArrayList<>(2);
        if (StrUtil.isNotBlank(member.getLoginName())) {
            String loginName = member.getLoginName().trim().toLowerCase(Locale.ROOT);
            if (!loginName.matches("[a-z][a-z0-9_]{5,19}")) {
                throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
            }
            credentials.add(new Credential("LOGIN_NAME", loginName));
        }
        String mobile = StrUtil.trim(member.getMobile());
        if (StrUtil.isNotBlank(mobile)) {
            if (!mobile.matches("1[3-9][0-9]{9}")) {
                throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
            }
            credentials.add(new Credential("MOBILE", mobile));
        }
        if (credentials.isEmpty()) {
            throw exception(PLATFORM_ACCOUNT_IDENTITY_CONFLICT);
        }
        return List.copyOf(credentials);
    }

    private static String credentialIdentifier(List<Credential> credentials, String type) {
        return credentials.stream().filter(credential -> Objects.equals(credential.type(), type))
                .map(Credential::identifier).findFirst().orElse(null);
    }

    private static Credential loginCredential(String account) {
        String identifier = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        if (identifier.matches("1[3-9][0-9]{9}")) {
            return new Credential("MOBILE", identifier);
        }
        if (identifier.matches("[a-z][a-z0-9_]{5,19}")) {
            return new Credential("LOGIN_NAME", identifier);
        }
        throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
    }

    private static String normalizedMobile(String mobile) {
        String normalized = StrUtil.trim(mobile);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private static String platformNo(Long memberId) {
        return "PM" + String.format(Locale.ROOT, "%018d", memberId);
    }

    private record Credential(String type, String identifier) { }

    private static MemberPlatformTenantMembershipRespDTO convertMembership(
            MemberPlatformTenantMembershipDO membership) {
        return new MemberPlatformTenantMembershipRespDTO()
                .setTenantId(membership.getTenantId())
                .setEnterpriseId(membership.getEnterpriseId())
                .setMemberType(membership.getMemberType());
    }
}
