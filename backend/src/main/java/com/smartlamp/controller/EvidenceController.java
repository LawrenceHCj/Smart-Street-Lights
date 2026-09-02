package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.EvidenceEntryDto;
import com.smartlamp.dto.EvidenceStatusDto;
import com.smartlamp.dto.EvidenceVerifyDto;
import com.smartlamp.service.EvidenceChainService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceChainService evidenceChainService;

    public EvidenceController(EvidenceChainService evidenceChainService) {
        this.evidenceChainService = evidenceChainService;
    }

    /** 轻量状态：只读链头 + 最新锚点元数据，不扫描整条链。admin/municipal/operator 可查看。 */
    @GetMapping("/{deviceCode}/status")
    @PreAuthorize("hasAnyRole('admin','municipal','operator')")
    public ApiResponse<EvidenceStatusDto> status(@PathVariable String deviceCode) {
        return ApiResponse.success(EvidenceStatusDto.from(deviceCode,
                evidenceChainService.getChainMetadata(deviceCode)));
    }

    /** 完整验证：扫描整条证据链，仅 admin/municipal。 */
    @GetMapping("/{deviceCode}/verify")
    @PreAuthorize("hasAnyRole('admin','municipal')")
    public ApiResponse<EvidenceVerifyDto> verify(@PathVariable String deviceCode) {
        return ApiResponse.success(EvidenceVerifyDto.from(deviceCode,
                evidenceChainService.verify(deviceCode)));
    }

    /** 证据条目分页详情（固定 seq 升序），仅 admin/municipal。 */
    @GetMapping("/{deviceCode}/entries")
    @PreAuthorize("hasAnyRole('admin','municipal')")
    public ApiResponse<Page<EvidenceEntryDto>> entries(
            @PathVariable String deviceCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long fromSeq,
            @RequestParam(required = false) Long toSeq,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String sourceType) {
        if (page < 0) {
            return ApiResponse.error(400, "page 不能小于 0");
        }
        if (size <= 0 || size > 200) {
            return ApiResponse.error(400, "size 必须在 1-200 之间");
        }
        if (fromSeq != null && fromSeq < 1) {
            return ApiResponse.error(400, "fromSeq 不能小于 1");
        }
        if (fromSeq != null && toSeq != null && toSeq < fromSeq) {
            return ApiResponse.error(400, "toSeq 不能小于 fromSeq");
        }
        Page<EvidenceEntryDto> result = evidenceChainService
                .getEntries(deviceCode, page, size, fromSeq, toSeq, eventType, sourceType)
                .map(EvidenceEntryDto::from);
        return ApiResponse.success(result);
    }
}
