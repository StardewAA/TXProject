import { requestClient } from '#/api/request';

export namespace HrmPortalAttendanceClockApi {
  /** 员工端打卡记录 */
  export interface AttendanceRecord {
    id: number; // 打卡记录编号
    employeeId: number; // 打卡员工编号
    clockTime: Date; // 打卡时间
    type?: number; // 打卡类型
    attendanceTime?: Date; // 应打卡时间
    sourceType?: number; // 打卡来源
    status?: number; // 打卡状态
    stage?: number; // 打卡阶段
    address?: string; // 打卡地址
    longitude?: number; // 经度
    latitude?: number; // 纬度
    ssid?: string; // WiFi 名称
    mac?: string; // WiFi MAC 地址
    remark?: string; // 备注
  }

  /** 打卡地点 */
  export interface Point {
    name?: string; // 地点名称
    address?: string; // 定位地址
    latitude?: number; // 纬度
    longitude?: number; // 经度
    radius?: number; // 有效半径（米）
  }

  /** WiFi 打卡配置 */
  export interface Wifi {
    ssid?: string; // WiFi 名称
    mac?: string; // MAC 地址
  }

  /** 下一次可打卡动作 */
  export interface NextClock {
    type?: number; // 打卡类型：1 上班 2 下班
    stage?: number; // 打卡阶段
    attendanceTime?: Date; // 应打卡时间
    buttonStatus?: number; // 0 未到时间 1 正常 2 更新 3 迟到 4 早退
  }

  /** 当日打卡时间线 */
  export interface TimelineItem {
    type?: number;
    attendanceTime?: Date;
    clockTime?: Date;
    status?: number;
    missCard?: boolean;
    address?: string;
    photoUrl?: string;
  }

  /** 我的打卡详情 */
  export interface ClockDetail {
    groupName?: string; // 考勤组名称
    openPointCard?: boolean; // 是否启用定位打卡
    openWifiCard?: boolean; // 是否启用 WiFi 打卡
    attendanceDate?: string; // 班次所属考勤日
    shiftTitle?: string; // 班次标题，如 09:00-18:00
    restDay?: boolean; // 是否休息日
    onDutyAttendanceTime?: Date; // 上班应打卡时间
    offDutyAttendanceTime?: Date; // 下班应打卡时间
    points?: Point[]; // 打卡地点列表
    wifis?: Wifi[]; // WiFi 列表
    nextClock?: NextClock; // 下一次可打卡动作
    timeline?: TimelineItem[]; // 当日打卡时间线
  }

  /** 手机端打卡请求 */
  export interface ClockCreateReqVO {
    address?: string; // 打卡地址
    longitude?: number; // 经度
    latitude?: number; // 纬度
    ssid?: string; // WiFi 名称
    mac?: string; // WiFi MAC 地址
    photoUrl?: string; // 打卡现场照片（后端必填）
  }
}

/**
 * 获得我的打卡详情（考勤组、班次、下一次可打卡动作、当日时间线）
 */
export function getAttendanceClockDetail() {
  return requestClient.get<HrmPortalAttendanceClockApi.ClockDetail>(
    '/hrm/portal/attendance/clock/get-detail',
  );
}

/**
 * 手机端打卡
 *
 * 注意：后端要求必须带现场照片（photoUrl）；考勤组开启定位打卡时需传经纬度，
 * 开启 WiFi 打卡时需传 ssid/mac（两者都开时满足其一即可）。
 */
export function createAttendanceClock(
  data: HrmPortalAttendanceClockApi.ClockCreateReqVO,
) {
  return requestClient.post<number>('/hrm/portal/attendance/clock/create', data);
}

/**
 * 获得我的考勤记录列表
 *
 * 说明：源 vue3 已提供该 API，当前 Portal 页面未引用（日明细由 statistics 承接）。
 * 保留迁移以与源 API 目录对齐，避免被误判为遗漏。
 */
export function getAttendanceRecordList(year?: number, month?: number) {
  return requestClient.get<HrmPortalAttendanceClockApi.AttendanceRecord[]>(
    '/hrm/portal/attendance/clock/list',
    { params: { year, month } },
  );
}
