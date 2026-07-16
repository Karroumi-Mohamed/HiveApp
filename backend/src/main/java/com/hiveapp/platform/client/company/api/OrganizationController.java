package com.hiveapp.platform.client.company.api;

import com.hiveapp.platform.client.company.dto.*;
import com.hiveapp.platform.client.company.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/groups")
    public List<OrganizationGroupDto> listGroups(@RequestParam UUID companyId) {
        return organizationService.listGroups(companyId);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationGroupDto createGroup(@Valid @RequestBody CreateOrganizationGroupRequest request) {
        return organizationService.createGroup(request.companyId(), request.parentId(), request.name(),
                request.description(), request.positionSuggestions());
    }

    @PatchMapping("/groups/{groupId}")
    public OrganizationGroupDto updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateOrganizationGroupRequest request) {
        return organizationService.updateGroup(groupId, request.name(), request.description(),
                request.positionSuggestions());
    }

    @PostMapping("/groups/{groupId}/move")
    public OrganizationGroupDto moveGroup(
            @PathVariable UUID groupId,
            @RequestBody MoveOrganizationGroupRequest request) {
        return organizationService.moveGroup(groupId, request.parentId());
    }

    @PutMapping("/groups/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderGroups(@Valid @RequestBody ReorderOrganizationGroupsRequest request) {
        organizationService.reorderGroups(request.companyId(), request.parentId(), request.orderedGroupIds());
    }

    @PostMapping("/groups/{groupId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveGroup(@PathVariable UUID groupId) {
        organizationService.archiveGroup(groupId);
    }

    @PostMapping("/groups/{groupId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreGroup(@PathVariable UUID groupId) {
        organizationService.restoreGroup(groupId);
    }

    @DeleteMapping("/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable UUID groupId) {
        organizationService.deleteGroup(groupId);
    }

    @GetMapping("/groups/{groupId}/members")
    public List<GroupMembershipDto> listMemberships(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "false") boolean includeDescendants) {
        return organizationService.listMemberships(groupId, includeDescendants);
    }

    @PutMapping("/groups/{groupId}/members/{memberId}")
    public GroupMembershipDto putMembership(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @Valid @RequestBody GroupMembershipRequest request) {
        return organizationService.putMembership(groupId, memberId, request.positionTitle());
    }

    @DeleteMapping("/groups/{groupId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMembership(@PathVariable UUID groupId, @PathVariable UUID memberId) {
        organizationService.removeMembership(groupId, memberId);
    }

    @GetMapping("/members/{memberId}/placements")
    public List<GroupMembershipDto> listMemberPlacements(
            @PathVariable UUID memberId,
            @RequestParam UUID companyId) {
        return organizationService.listMemberPlacements(companyId, memberId);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupTemplateDto createTemplate(@Valid @RequestBody CreateGroupTemplateRequest request) {
        return organizationService.createTemplate(request.companyId(), request.sourceGroupId(),
                request.scope(), request.name());
    }

    @GetMapping("/templates")
    public List<GroupTemplateDto> listTemplates(@RequestParam UUID companyId) {
        return organizationService.listTemplates(companyId);
    }

    @GetMapping("/templates/{templateId}/preview")
    public GroupTemplatePreviewDto previewTemplate(
            @PathVariable UUID templateId,
            @RequestParam UUID companyId,
            @RequestParam(required = false) UUID parentId) {
        return organizationService.previewTemplate(templateId, companyId, parentId);
    }

    @PostMapping("/templates/{templateId}/instantiate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<OrganizationGroupDto> instantiateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody InstantiateGroupTemplateRequest request) {
        return organizationService.instantiateTemplate(templateId, request.companyId(), request.parentId());
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable UUID templateId) {
        organizationService.deleteTemplate(templateId);
    }
}
