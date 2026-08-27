package com.smartlamp.agent.tools;

import com.smartlamp.dto.DeviceDTO;
import com.smartlamp.dto.LightDataDTO;
import com.smartlamp.dto.LightHistoryDTO;
import com.smartlamp.dto.LinkageConfigDTO;
import com.smartlamp.entity.Alarm;
import com.smartlamp.exception.BadRequestException;
import com.smartlamp.service.AlarmService;
import com.smartlamp.service.ConfigService;
import com.smartlamp.service.DeviceService;
import com.smartlamp.service.LightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

// 系统数据只读工具：智能体查询真实系统数据的唯一入口。
// 只注入 3号成员 Service 的只读方法（get*），绝不调用 add/remove/ack/save 等写方法，也不直接访问 Repository。
@Component
public class AgentTools {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private LightService lightService;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private ConfigService configService;

    // ============ 设备 ============

    // 查询全部设备列表（只读）
    public List<DeviceDTO> getDeviceList() {
        List<DeviceDTO> devices = deviceService.getAllDeviceDTOs();
        return devices == null ? List.of() : devices;
    }

    // 查询单台设备状态（只读）；设备不存在返回 null
    public DeviceDTO getDeviceStatus(String deviceCode) {
        String code = requireText(deviceCode, "deviceCode");
        return getDeviceList().stream()
                .filter(device -> code.equals(device.getCode()))
                .findFirst()
                .orElse(null);
    }

    // ============ 光照 ============

    // 查询单台设备最新光照（只读）；设备不存在或无数据返回 null
    public LightDataDTO getLatestTelemetry(String deviceCode) {
        String code = requireText(deviceCode, "deviceCode");
        return deviceService.getCurrentLight(code);
    }

    // 查询单台设备光照历史（只读）；时间戳为毫秒，start 必须小于等于 end
    public LightHistoryDTO getTelemetryHistory(String deviceCode, Long start, Long end) {
        String code = requireText(deviceCode, "deviceCode");
        if (start == null || end == null) {
            throw new BadRequestException("start 和 end 不能为空");
        }
        if (start > end) {
            throw new BadRequestException("start 不能大于 end");
        }
        return lightService.getHistory(code, start, end);
    }

    // ============ 告警 ============

    // 查询全部告警记录（只读，按时间倒序）
    public List<Alarm> getAlertHistory() {
        List<Alarm> alarms = alarmService.getAllAlarms();
        return alarms == null ? List.of() : alarms;
    }

    // 查询指定设备的告警记录（只读，按时间倒序）
    public List<Alarm> getAlertHistory(String deviceCode) {
        String code = requireText(deviceCode, "deviceCode");
        return getAlertHistory().stream()
                .filter(alarm -> code.equals(alarm.getDeviceId()))
                .toList();
    }

    // ============ 配置 ============

    // 查询光照联动配置（只读，附带能力：智能体回答阈值问题时取真实配置）
    public LinkageConfigDTO getLinkageConfig() {
        return configService.getLinkageConfig();
    }

    // 参数校验：文本参数非空，返回 trim 后的值
    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(name + " 不能为空");
        }
        return value.trim();
    }
}
