package cn.iocoder.txgy.module.member.service.address;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.controller.app.address.vo.AppAddressCreateReqVO;
import cn.iocoder.txgy.module.member.controller.app.address.vo.AppAddressLocationCapabilityRespVO;
import cn.iocoder.txgy.module.member.dal.dataobject.address.MemberAddressDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AddressLocationPolicyTest {
    private AppAddressCreateReqVO request() {
        var v = new AppAddressCreateReqVO(); v.setAreaId(110101L).setDetailAddress("测试工地一号入口"); return v;
    }
    private AppAddressCreateReqVO located() {
        var v = request(); v.setLongitude(new BigDecimal("116.397428")).setLatitude(new BigDecimal("39.90923"))
                .setCoordSystem("GCJ02").setLocationSource("MAP_PICKER").setLocationPrecision("BUILDING").setLocationConfirmed(true); return v;
    }
    private MemberAddressDO old() {
        var v = new MemberAddressDO().setAreaId(110101L).setDetailAddress("测试工地一号入口");
        AddressLocationPolicy.apply(located(), null, v); return v;
    }
    @Test void manualAddressRemainsValid() {
        var v = new MemberAddressDO(); AddressLocationPolicy.apply(request(), null, v); assertNull(v.getLongitude());
    }
    @Test void confirmedPointUsesServerTime() {
        var v = new MemberAddressDO(); var input = located(); input.setLocationConfirmedAt(LocalDateTime.of(2000,1,1,0,0));
        AddressLocationPolicy.apply(input,null,v); assertEquals(input.getLatitude(),v.getLatitude());
        assertTrue(v.getLocationConfirmedAt().isAfter(LocalDateTime.now().minusMinutes(1)));
    }
    @Test void oldClientRecipientOnlyEditPreservesPoint() {
        var old=old(); var v=new MemberAddressDO(); AddressLocationPolicy.apply(request(),old,v);
        assertEquals(old.getLocationConfirmedAt(),v.getLocationConfirmedAt());
    }
    @Test void manualTextChangeClearsPoint() {
        var input=request();input.setDetailAddress("测试工地二号入口");var v=old();AddressLocationPolicy.apply(input,old(),v);
        assertNull(v.getLatitude()); assertNull(v.getCoordSystem()); assertNull(v.getLocationConfirmedAt());
    }
    @Test void echoedPointWithChangedAddressIsClearedUnlessReconfirmed() {
        var input=located();input.setDetailAddress("测试工地二号入口").setLocationConfirmed(false);
        var v=new MemberAddressDO();AddressLocationPolicy.apply(input,old(),v);assertNull(v.getLongitude());
        input.setLocationConfirmed(true);AddressLocationPolicy.apply(input,old(),v);assertNotNull(v.getLongitude());
    }
    @Test void unchangedEchoPreservesConfirmationTime() {
        var old=old();var input=located();input.setLocationConfirmed(false);var v=new MemberAddressDO();
        AddressLocationPolicy.apply(input,old,v);assertEquals(old.getLocationConfirmedAt(),v.getLocationConfirmedAt());
    }
    @Test void newPointNeedsExplicitConfirmation() {
        var input=located();input.setLocationConfirmed(false);assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void changedPointNeedsExplicitConfirmation() {
        var input=located();input.setLongitude(BigDecimal.ONE).setLocationConfirmed(false);
        assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,old(),new MemberAddressDO()));
    }
    @Test void halfCoordinateRejected() {
        var input=located();input.setLatitude(null);assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void missingMetadataRejectedWithoutNullPointer() {
        var input=located();input.setLocationSource(null);assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
        input.setLocationSource("MAP_PICKER").setLocationPrecision(null);assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @ParameterizedTest @ValueSource(strings={"180.000001","-180.000001","116.1234567"})
    void invalidLongitudeRejected(String coordinate) {
        var input=located();input.setLongitude(new BigDecimal(coordinate));assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void invalidLatitudeRejected() {
        var input=located();input.setLatitude(new BigDecimal("90.000001"));assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void unsupportedCoordinateSystemRejected() {
        var input=located();input.setCoordSystem("WGS84");assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void precisionAndSourceCannotClaimVehicleGps() {
        var input=located();input.setLocationSource("VEHICLE_GPS");assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void explicitClearDropsAllMetadata() {
        var input=request();input.setClearLocation(true);var v=old();AddressLocationPolicy.apply(input,old(),v);assertNull(v.getLongitude());assertNull(v.getLocationConfirmedAt());
    }
    @Test void contradictoryClearRejected() {
        var input=located();input.setClearLocation(true);assertThrows(ServiceException.class,()->AddressLocationPolicy.apply(input,null,new MemberAddressDO()));
    }
    @Test void capabilityContainsNoKeysOrFakeAvailability() throws Exception {
        String json=new ObjectMapper().writeValueAsString(new AppAddressLocationCapabilityRespVO());
        assertTrue(json.contains("NOT_CONFIGURED"));assertTrue(json.contains("\"enabled\":false"));
        assertTrue(json.contains("\"manualInputEnabled\":true"));assertFalse(json.toLowerCase().contains("key"));
    }
}
