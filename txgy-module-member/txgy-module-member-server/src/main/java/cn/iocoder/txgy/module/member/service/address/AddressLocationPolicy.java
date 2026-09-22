package cn.iocoder.txgy.module.member.service.address;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.module.member.controller.app.address.vo.AppAddressBaseVO;
import cn.iocoder.txgy.module.member.dal.dataobject.address.MemberAddressDO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Pure address policy. A client-confirmed point is not verified geocoding or delivery evidence. */
public final class AddressLocationPolicy {
    public static final ErrorCode INVALID = new ErrorCode(1004008300, "地址点位无效，请重新选点或使用手工地址");
    public static final ErrorCode CONFIRM_REQUIRED = new ErrorCode(1004008301, "请确认点位与收货地址一致后保存");
    private static final Set<String> SOURCES = Set.of("MAP_PICKER", "DEVICE_LOCATION", "GEOCODER");
    private static final Set<String> PRECISIONS = Set.of("ADDRESS", "BUILDING", "APPROXIMATE");
    private AddressLocationPolicy() { }

    public static void apply(AppAddressBaseVO input, MemberAddressDO old, MemberAddressDO target) {
        clear(target);
        boolean any = input.getLongitude() != null || input.getLatitude() != null
                || input.getCoordSystem() != null || input.getLocationSource() != null
                || input.getLocationPrecision() != null;
        if (Boolean.TRUE.equals(input.getClearLocation())) {
            if (any || Boolean.TRUE.equals(input.getLocationConfirmed())) throw exception(INVALID);
            return;
        }
        boolean sameAddress = old != null && Objects.equals(old.getAreaId(), input.getAreaId())
                && Objects.equals(old.getDetailAddress(), input.getDetailAddress());
        if (!any) {
            if (Boolean.TRUE.equals(input.getLocationConfirmed())) throw exception(INVALID);
            if (sameAddress) copy(old, target); // old clients may edit a recipient/default flag without a map
            return; // changed written address invalidates the previous point
        }
        if (!validCoordinate(input.getLongitude(), 180) || !validCoordinate(input.getLatitude(), 90)
                || !"GCJ02".equals(input.getCoordSystem()) || input.getLocationSource() == null
                || !SOURCES.contains(input.getLocationSource()) || input.getLocationPrecision() == null
                || !PRECISIONS.contains(input.getLocationPrecision())) throw exception(INVALID);
        boolean samePoint = old != null && equal(input.getLongitude(), old.getLongitude())
                && equal(input.getLatitude(), old.getLatitude())
                && Objects.equals(input.getCoordSystem(), old.getCoordSystem())
                && Objects.equals(input.getLocationSource(), old.getLocationSource())
                && Objects.equals(input.getLocationPrecision(), old.getLocationPrecision());
        if (!Boolean.TRUE.equals(input.getLocationConfirmed())) {
            if (sameAddress && samePoint && old.getLocationConfirmedAt() != null) copy(old, target);
            else if (!sameAddress && samePoint) return; // echoed stale point must never follow a text edit
            else throw exception(CONFIRM_REQUIRED);
            return;
        }
        target.setLongitude(input.getLongitude()).setLatitude(input.getLatitude())
                .setCoordSystem(input.getCoordSystem()).setLocationSource(input.getLocationSource())
                .setLocationPrecision(input.getLocationPrecision()).setLocationConfirmedAt(LocalDateTime.now());
    }

    private static boolean validCoordinate(BigDecimal value, int limit) {
        return value != null && value.scale() <= 6 && value.abs().compareTo(BigDecimal.valueOf(limit)) <= 0;
    }
    private static boolean equal(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private static void copy(MemberAddressDO from, MemberAddressDO to) {
        to.setLongitude(from.getLongitude()).setLatitude(from.getLatitude()).setCoordSystem(from.getCoordSystem())
                .setLocationSource(from.getLocationSource()).setLocationPrecision(from.getLocationPrecision())
                .setLocationConfirmedAt(from.getLocationConfirmedAt());
    }
    private static void clear(MemberAddressDO value) {
        value.setLongitude(null).setLatitude(null).setCoordSystem(null).setLocationSource(null)
                .setLocationPrecision(null).setLocationConfirmedAt(null);
    }
}
