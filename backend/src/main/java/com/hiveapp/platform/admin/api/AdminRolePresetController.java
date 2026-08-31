package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.dto.AdminRolePresetDto;
import com.hiveapp.platform.admin.service.AdminRoleService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/role-presets")
@RequiredArgsConstructor
public class AdminRolePresetController {
    private final AdminRoleService adminRoleService;

    @GetMapping
    public List<AdminRolePresetDto> getAvailable() {
        return adminRoleService.getAvailablePresets();
    }

    @GetMapping("/{code}")
    public AdminRolePresetDto get(@PathVariable String code) {
        return adminRoleService.getAvailablePresets().stream()
                .filter(preset -> preset.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("AdminRolePreset", "code", code));
    }
}
