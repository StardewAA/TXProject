package cn.iocoder.txgy.module.member.service.platform;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformTenantMembershipDO;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAccountMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAddressMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformTenantMembershipMapper;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_NOT_READY;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ADDRESS_NOT_FOUND;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_IDENTITY_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MemberPlatformAccountServiceImplTest {

    private final MemberPlatformAccountMapper accountMapper = mock(MemberPlatformAccountMapper.class);
    private final MemberPlatformTenantMembershipMapper membershipMapper =
            mock(MemberPlatformTenantMembershipMapper.class);
    private final MemberPlatformAddressMapper addressMapper = mock(MemberPlatformAddressMapper.class);
    private final MemberPlatformAccountServiceImpl service = new MemberPlatformAccountServiceImpl();

    MemberPlatformAccountServiceImplTest() {
        ReflectionTestUtils.setField(service, "platformAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "membershipMapper", membershipMapper);
        ReflectionTestUtils.setField(service, "platformAddressMapper", addressMapper);
    }

    @Test
    void resolvesOnlyReadyMappingAndDropsPublicTenantMembership() {
        when(accountMapper.selectReadyByLegacySession(316L, 1L)).thenReturn(
                MemberPlatformAccountDO.builder().id(8L).platformNo("PM0008").canonicalMemberId(316L)
                        .status(0).build());
        when(membershipMapper.selectActiveByPlatformAccountId(8L)).thenReturn(List.of(
                MemberPlatformTenantMembershipDO.builder().tenantId(1L).enterpriseId(910123L)
                        .memberType("BUYER").status(0).build()));

        var result = service.resolveRequired(316L, 1L);

        assertEquals(8L, result.getPlatformAccountId());
        assertEquals(316L, result.getLegacyMemberId());
        assertEquals(1L, result.getSourceTenantId());
        assertEquals(1, result.getMemberships().size());
        assertEquals(910123L, result.getMemberships().get(0).getEnterpriseId());
    }

    @Test
    void rejectsUnmappedOrPublicTenantSession() {
        ServiceException missing = assertThrows(ServiceException.class,
                () -> service.resolveRequired(999L, 1L));
        assertEquals(PLATFORM_ACCOUNT_NOT_READY.getCode(), missing.getCode());

        ServiceException publicTenant = assertThrows(ServiceException.class,
                () -> service.resolveRequired(1L, 3000L));
        assertEquals(PLATFORM_ACCOUNT_NOT_READY.getCode(), publicTenant.getCode());
        verifyNoInteractions(membershipMapper);
    }

    @Test
    void resolvesOnlyAddressBoundToReadyLegacySession() {
        when(accountMapper.selectReadyByLegacySession(316L, 1L)).thenReturn(
                MemberPlatformAccountDO.builder().id(8L).platformNo("PM0008").canonicalMemberId(316L)
                        .status(0).build());
        when(membershipMapper.selectActiveByPlatformAccountId(8L)).thenReturn(List.of());
        var address = new cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO()
                .setPlatformAccountId(8L).setLegacyMemberId(400L).setSourceTenantId(2009L)
                .setAddressId(99L).setName("采购员").setMobile("13600000000")
                .setAreaId(610100L).setDetailAddress("高新区 1 号");
        when(addressMapper.selectLinkedAddress(8L, 316L, 1L, 99L)).thenReturn(address);

        var result = service.resolveAddressRequired(316L, 1L, 99L);

        assertEquals(99L, result.getAddressId());
        assertEquals(8L, result.getPlatformAccountId());
    }

    @Test
    void rejectsAddressFromAnotherLegacyMemberOrTenant() {
        when(accountMapper.selectReadyByLegacySession(316L, 1L)).thenReturn(
                MemberPlatformAccountDO.builder().id(8L).platformNo("PM0008").canonicalMemberId(316L)
                        .status(0).build());
        when(membershipMapper.selectActiveByPlatformAccountId(8L)).thenReturn(List.of());
        when(addressMapper.selectLinkedAddress(8L, 316L, 1L, 99L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveAddressRequired(316L, 1L, 99L));

        assertEquals(PLATFORM_ADDRESS_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void rejectsLegacySessionWhenMultiMemberIdentityHasNoSingleManualReview() {
        when(accountMapper.selectReadyByLegacySession(316L, 1L)).thenReturn(
                MemberPlatformAccountDO.builder().id(8L).platformNo("PM0008").canonicalMemberId(316L)
                        .status(0).build());
        when(accountMapper.countUnsafeReadyIdentityGraph(8L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveRequired(316L, 1L));

        assertEquals(PLATFORM_ACCOUNT_NOT_READY.getCode(), error.getCode());
        verifyNoInteractions(membershipMapper);
    }

    @Test
    void registrationCreatesGloballyIdentifiedAccountAndReadyLink() {
        MemberUserDO member = new MemberUserDO().setId(81L).setLoginName("buyer_1")
                .setMobile("13900009991").setPassword("hash").setStatus(0);
        when(accountMapper.countMembersByCredential("LOGIN_NAME", "buyer_1")).thenReturn(1L);
        when(accountMapper.countMembersByCredential("MOBILE", "13900009991")).thenReturn(1L);
        when(accountMapper.insertRegistrationAccount(any())).thenAnswer(call -> {
            MemberPlatformAccountDO row = call.getArgument(0);
            row.setId(9L);
            return 1;
        });
        when(accountMapper.insertRegistrationLink(9L, 81L, 9001L)).thenReturn(1);

        var result = service.createForRegistration(member, 9001L);

        assertEquals(9L, result.getPlatformAccountId());
        assertEquals("PM000000000000000081", result.getPlatformNo());
        verify(accountMapper).insertRegistrationAccount(argThat(row ->
                "LOGIN_NAME".equals(row.getCredentialType())
                        && "buyer_1".equals(row.getCredentialIdentifier())
                        && "buyer_1".equals(row.getLoginName())
                        && "13900009991".equals(row.getMobile())
                        && Long.valueOf(81L).equals(row.getCanonicalMemberId())));
        verify(accountMapper).insertRegistrationLink(9L, 81L, 9001L);
    }

    @Test
    void registrationFailsClosedForPublicTenantOrExistingGlobalIdentifier() {
        MemberUserDO member = new MemberUserDO().setId(81L).setLoginName("buyer_1");
        ServiceException publicTenant = assertThrows(ServiceException.class,
                () -> service.createForRegistration(member, 3000L));
        assertEquals(PLATFORM_ACCOUNT_IDENTITY_CONFLICT.getCode(), publicTenant.getCode());

        when(accountMapper.countMembersByCredential("LOGIN_NAME", "buyer_1")).thenReturn(2L);
        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.createForRegistration(member, 9001L));
        assertEquals(PLATFORM_ACCOUNT_IDENTITY_CONFLICT.getCode(), conflict.getCode());
        verify(accountMapper, never()).insertRegistrationAccount(any());
        verify(accountMapper, never()).insertRegistrationLink(anyLong(), anyLong(), anyLong());
    }

    @Test
    void registrationFailsClosedWhenSecondaryMobileBelongsToAnotherPlatformIdentity() {
        MemberUserDO member = new MemberUserDO().setId(81L).setLoginName("buyer_1")
                .setMobile("13900009991");
        when(accountMapper.countMembersByCredential("LOGIN_NAME", "buyer_1")).thenReturn(1L);
        when(accountMapper.countMembersByCredential("MOBILE", "13900009991")).thenReturn(2L);

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.createForRegistration(member, 9001L));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_CONFLICT.getCode(), conflict.getCode());
        verify(accountMapper, never()).insertRegistrationAccount(any());
        verify(accountMapper, never()).insertRegistrationLink(anyLong(), anyLong(), anyLong());
    }

    @Test
    void registrationRetryOnlyAcceptsMatchingReadyLink() {
        MemberUserDO member = new MemberUserDO().setId(81L).setMobile("13900009991");
        when(accountMapper.selectReadyRegistrationLink(81L, 9001L)).thenReturn(
                MemberPlatformAccountDO.builder().id(9L).platformNo("PM000000000000000081")
                        .credentialType("MOBILE").credentialIdentifier("13900009991")
                        .mobile("13900009991")
                        .canonicalMemberId(81L).status(0).build());
        when(accountMapper.countMembersByCredential("MOBILE", "13900009991")).thenReturn(1L);

        var result = service.createForRegistration(member, 9001L);

        assertEquals(9L, result.getPlatformAccountId());
        verify(accountMapper).countMembersByCredential("MOBILE", "13900009991");
        verify(accountMapper, never()).insertRegistrationAccount(any());
    }

    @Test
    void mobileAliasUpdateRequiresOneReadyLinkAndUpdatesPrimaryCredentialTogether() {
        MemberUserDO member = new MemberUserDO().setId(81L).setMobile("13900009991");
        member.setTenantId(9001L);
        MemberPlatformAccountDO account = MemberPlatformAccountDO.builder().id(9L)
                .credentialType("MOBILE").credentialIdentifier("13900009991")
                .mobile("13900009991").canonicalMemberId(81L).status(0).build();
        when(accountMapper.countActiveLinksByLegacyMember(81L, 9001L)).thenReturn(1L);
        when(accountMapper.selectReadyRegistrationLink(81L, 9001L)).thenReturn(account);
        when(accountMapper.countReadyLinksByPlatformAccount(9L)).thenReturn(1L);
        when(accountMapper.updateMobileAlias(9L, "13900009991", "13900009992")).thenReturn(1);

        service.synchronizeMobileAlias(member, " 13900009992 ");

        verify(accountMapper).countOtherMembersByMobile("13900009992", 81L, 9001L);
        verify(accountMapper).countOtherPlatformAccountsByMobile(9L, "13900009992");
        verify(accountMapper).updateMobileAlias(9L, "13900009991", "13900009992");
    }

    @Test
    void mobileAliasUpdateFailsClosedForAmbiguousMappingOrGlobalConflict() {
        MemberUserDO member = new MemberUserDO().setId(81L).setMobile("13900009991");
        member.setTenantId(9001L);
        when(accountMapper.countActiveLinksByLegacyMember(81L, 9001L)).thenReturn(2L);
        ServiceException ambiguous = assertThrows(ServiceException.class,
                () -> service.synchronizeMobileAlias(member, "13900009992"));
        assertEquals(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT.getCode(), ambiguous.getCode());

        reset(accountMapper);
        MemberPlatformAccountDO account = MemberPlatformAccountDO.builder().id(9L)
                .credentialType("LOGIN_NAME").credentialIdentifier("buyer_1")
                .loginName("buyer_1").mobile("13900009991")
                .canonicalMemberId(81L).status(0).build();
        when(accountMapper.countActiveLinksByLegacyMember(81L, 9001L)).thenReturn(1L);
        when(accountMapper.selectReadyRegistrationLink(81L, 9001L)).thenReturn(account);
        when(accountMapper.countReadyLinksByPlatformAccount(9L)).thenReturn(1L);
        when(accountMapper.countOtherMembersByMobile("13900009992", 81L, 9001L)).thenReturn(1L);
        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.synchronizeMobileAlias(member, "13900009992"));
        assertEquals(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT.getCode(), conflict.getCode());
        verify(accountMapper, never()).updateMobileAlias(anyLong(), any(), any());
    }

    @Test
    void unlinkedLegacyMemberCannotTakeAnotherAccountsGlobalMobile() {
        MemberUserDO member = new MemberUserDO().setId(81L).setMobile("13900009991");
        member.setTenantId(9001L);
        when(accountMapper.countOtherMembersByMobile("13900009992", 81L, 9001L)).thenReturn(1L);

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.synchronizeMobileAlias(member, "13900009992"));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT.getCode(), conflict.getCode());
        verify(accountMapper, never()).countActiveLinksByLegacyMember(anyLong(), anyLong());
        verify(accountMapper, never()).updateMobileAlias(anyLong(), any(), any());
    }

    @Test
    void mobileAliasUpdateConvertsConcurrentUniqueViolationToBusinessError() {
        MemberUserDO member = new MemberUserDO().setId(81L).setMobile("13900009991");
        member.setTenantId(9001L);
        MemberPlatformAccountDO account = MemberPlatformAccountDO.builder().id(9L)
                .credentialType("MOBILE").credentialIdentifier("13900009991")
                .mobile("13900009991").canonicalMemberId(81L).status(0).build();
        when(accountMapper.countActiveLinksByLegacyMember(81L, 9001L)).thenReturn(1L);
        when(accountMapper.selectReadyRegistrationLink(81L, 9001L)).thenReturn(account);
        when(accountMapper.countReadyLinksByPlatformAccount(9L)).thenReturn(1L);
        when(accountMapper.updateMobileAlias(9L, "13900009991", "13900009992"))
                .thenThrow(new DuplicateKeyException("private unique-index detail"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.synchronizeMobileAlias(member, "13900009992"));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void unifiedLoginRequiresOneCompleteCredentialSetAndOneHash() {
        MemberPlatformLoginCandidate candidate = new MemberPlatformLoginCandidate()
                .setPlatformAccountId(9L).setPlatformNo("PM000000000000000081")
                .setCredentialType("LOGIN_NAME").setCredentialIdentifier("buyer_1")
                .setCanonicalMemberId(81L).setSourceTenantId(9001L);
        when(accountMapper.selectLoginCandidates("LOGIN_NAME", "buyer_1")).thenReturn(List.of(candidate));
        when(accountMapper.countMembersByCredential("LOGIN_NAME", "buyer_1")).thenReturn(2L);
        when(accountMapper.countReadyMembersByCredential(9L, "LOGIN_NAME", "buyer_1")).thenReturn(2L);
        when(accountMapper.countReadyCredentialHashes(9L, "LOGIN_NAME", "buyer_1")).thenReturn(1L);

        var resolved = service.resolveLoginRequired(" Buyer_1 ");

        assertEquals(81L, resolved.getCanonicalMemberId());
        assertEquals(9001L, resolved.getSourceTenantId());
    }

    @Test
    void unifiedLoginCanUseTheGloballyBoundSecondaryMobile() {
        MemberPlatformLoginCandidate candidate = new MemberPlatformLoginCandidate()
                .setPlatformAccountId(9L).setPlatformNo("PM000000000000000081")
                .setCredentialType("MOBILE").setCredentialIdentifier("13900009991")
                .setCanonicalMemberId(81L).setSourceTenantId(9001L);
        when(accountMapper.selectLoginCandidates("MOBILE", "13900009991")).thenReturn(List.of(candidate));
        when(accountMapper.countMembersByCredential("MOBILE", "13900009991")).thenReturn(1L);
        when(accountMapper.countReadyMembersByCredential(9L, "MOBILE", "13900009991")).thenReturn(1L);
        when(accountMapper.countReadyCredentialHashes(9L, "MOBILE", "13900009991")).thenReturn(1L);

        var resolved = service.resolveLoginRequired(" 13900009991 ");

        assertEquals(81L, resolved.getCanonicalMemberId());
        assertEquals("MOBILE", resolved.getCredentialType());
    }

    @Test
    void unifiedLoginHidesMissingAndConflictingIdentityBehindSameCredentialError() {
        when(accountMapper.selectLoginCandidates("LOGIN_NAME", "buyer_1")).thenReturn(List.of());
        ServiceException missing = assertThrows(ServiceException.class,
                () -> service.resolveLoginRequired("buyer_1"));
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), missing.getCode());

        MemberPlatformLoginCandidate candidate = new MemberPlatformLoginCandidate()
                .setPlatformAccountId(9L).setCredentialType("LOGIN_NAME")
                .setCredentialIdentifier("buyer_1").setCanonicalMemberId(81L).setSourceTenantId(9001L);
        when(accountMapper.selectLoginCandidates("LOGIN_NAME", "buyer_1")).thenReturn(List.of(candidate));
        when(accountMapper.countMembersByCredential("LOGIN_NAME", "buyer_1")).thenReturn(2L);
        when(accountMapper.countReadyMembersByCredential(9L, "LOGIN_NAME", "buyer_1")).thenReturn(1L);
        when(accountMapper.countReadyCredentialHashes(9L, "LOGIN_NAME", "buyer_1")).thenReturn(1L);
        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.resolveLoginRequired("buyer_1"));
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), conflict.getCode());
    }

    @Test
    void unifiedLoginDoesNotGuessMergeFromMatchingCredentialAndPasswordHash() {
        MemberPlatformLoginCandidate candidate = new MemberPlatformLoginCandidate()
                .setPlatformAccountId(9L).setPlatformNo("PM000000000000000081")
                .setCredentialType("LOGIN_NAME").setCredentialIdentifier("buyer_1")
                .setCanonicalMemberId(81L).setSourceTenantId(9001L);
        when(accountMapper.selectLoginCandidates("LOGIN_NAME", "buyer_1")).thenReturn(List.of(candidate));
        when(accountMapper.countMembersByCredential("LOGIN_NAME", "buyer_1")).thenReturn(2L);
        when(accountMapper.countReadyMembersByCredential(9L, "LOGIN_NAME", "buyer_1")).thenReturn(2L);
        when(accountMapper.countReadyCredentialHashes(9L, "LOGIN_NAME", "buyer_1")).thenReturn(1L);
        when(accountMapper.countUnsafeReadyIdentityGraph(9L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveLoginRequired("buyer_1"));

        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), error.getCode());
    }
}
