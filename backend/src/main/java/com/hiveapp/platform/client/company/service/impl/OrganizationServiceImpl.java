package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;
import com.hiveapp.platform.client.company.domain.entity.*;
import com.hiveapp.platform.client.company.domain.repository.*;
import com.hiveapp.platform.client.company.dto.*;
import com.hiveapp.platform.client.company.service.OrganizationService;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.*;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PermissionNode(key = OrganizationFeature.KEY, description = "Organization Group Management", guard = PermissionNode.Guard.ON)
public class OrganizationServiceImpl extends ClientWorkspaceFeatureService implements OrganizationService {

    private static final UUID ROOT_SCOPE_KEY = new UUID(0L, 0L);

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final OrganizationGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupStructureTemplateRepository templateRepository;
    private final GroupTemplateNodeRepository templateNodeRepository;

    @Override
    protected FeatureDefinition featureDefinition() {
        return OrganizationFeature.definition();
    }

    @Override
    @PermissionNode(key = OrganizationFeature.LIST_GROUPS, description = "View organization structure")
    public List<OrganizationGroupDto> listGroups(UUID companyId) {
        requireCompany(companyId);
        List<OrganizationGroup> groups = groupRepository.findAllByCompanyIdOrderByDisplayOrderAscNameAsc(companyId);
        Map<UUID, Long> memberCounts = groups.isEmpty() ? Map.of() : membershipRepository
                .findAllByGroupIdIn(groups.stream().map(OrganizationGroup::getId).toList()).stream()
                .collect(Collectors.groupingBy(m -> m.getGroup().getId(), Collectors.counting()));
        return groups.stream().map(group -> toDto(group, memberCounts.getOrDefault(group.getId(), 0L))).toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.CREATE, description = "Create organization group")
    public OrganizationGroupDto createGroup(UUID companyId, UUID parentId, String name, String description, List<String> positions) {
        Company company = requireActiveCompany(companyId);
        OrganizationGroup parent = parentId == null ? null : requireGroup(parentId);
        requireSameCompany(company, parent);
        requireActiveParent(parent);
        String displayName = normalizeRequiredName(name);
        String normalized = normalizeName(displayName);
        UUID parentKey = scopeKey(parent);
        rejectSiblingDuplicate(companyId, parentKey, normalized, null);

        List<OrganizationGroup> siblings = groupRepository
                .findAllByCompanyIdAndParentScopeKeyOrderByDisplayOrderAscNameAsc(companyId, parentKey);
        OrganizationGroup group = new OrganizationGroup();
        group.setCompany(company);
        group.setParent(parent);
        group.setName(displayName);
        group.setNormalizedName(normalized);
        group.setDescription(normalizeOptional(description));
        group.setPositionSuggestions(normalizePositions(positions));
        group.setDisplayOrder(nextOrder(siblings));
        group.setStatus(GroupStatus.ACTIVE);
        return toDto(groupRepository.save(group), 0L);
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.UPDATE, description = "Update organization group")
    public OrganizationGroupDto updateGroup(UUID groupId, String name, String description, List<String> positions) {
        OrganizationGroup group = requireMutableGroup(groupId);
        if (name != null) {
            String displayName = normalizeRequiredName(name);
            String normalized = normalizeName(displayName);
            rejectSiblingDuplicate(group.getCompany().getId(), scopeKey(group.getParent()), normalized, groupId);
            group.setName(displayName);
            group.setNormalizedName(normalized);
        }
        if (description != null) group.setDescription(normalizeOptional(description));
        if (positions != null) group.setPositionSuggestions(normalizePositions(positions));
        return toDto(groupRepository.save(group), membershipRepository.countByGroupId(groupId));
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.MOVE, description = "Move organization group subtree")
    public OrganizationGroupDto moveGroup(UUID groupId, UUID parentId) {
        OrganizationGroup group = requireMutableGroup(groupId);
        OrganizationGroup parent = parentId == null ? null : requireGroup(parentId);
        requireSameCompany(group.getCompany(), parent);
        requireActiveParent(parent);
        for (OrganizationGroup cursor = parent; cursor != null; cursor = cursor.getParent()) {
            if (groupId.equals(cursor.getId())) {
                throw new InvalidStateException("A Group cannot be moved inside its own subtree");
            }
        }
        rejectSiblingDuplicate(
                group.getCompany().getId(), scopeKey(parent), group.getNormalizedName(), groupId);
        List<OrganizationGroup> siblings = groupRepository.findAllByCompanyIdAndParentScopeKeyOrderByDisplayOrderAscNameAsc(
                group.getCompany().getId(), scopeKey(parent));
        group.setParent(parent);
        group.setDisplayOrder(nextOrder(siblings));
        return toDto(groupRepository.save(group), membershipRepository.countByGroupId(groupId));
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.REORDER, description = "Reorder sibling organization groups")
    public void reorderGroups(UUID companyId, UUID parentId, List<UUID> orderedGroupIds) {
        Company company = requireActiveCompany(companyId);
        OrganizationGroup parent = parentId == null ? null : requireGroup(parentId);
        requireSameCompany(company, parent);
        List<OrganizationGroup> siblings = groupRepository.findForUpdateByCompanyIdAndParentScopeKey(companyId, scopeKey(parent));
        Set<UUID> requested = new LinkedHashSet<>(orderedGroupIds);
        Set<UUID> actual = siblings.stream().map(OrganizationGroup::getId).collect(Collectors.toSet());
        if (requested.size() != orderedGroupIds.size() || !requested.equals(actual)) {
            throw new InvalidRequestException("Reorder must contain every sibling exactly once and no other Group");
        }
        Map<UUID, OrganizationGroup> byId = siblings.stream().collect(Collectors.toMap(OrganizationGroup::getId, Function.identity()));
        for (int index = 0; index < orderedGroupIds.size(); index++) {
            byId.get(orderedGroupIds.get(index)).setDisplayOrder(index);
        }
        groupRepository.saveAll(siblings);
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.ARCHIVE, description = "Archive organization group subtree")
    public void archiveGroup(UUID groupId) {
        setSubtreeStatus(requireMutableGroup(groupId), GroupStatus.ARCHIVED);
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.RESTORE, description = "Restore organization group subtree")
    public void restoreGroup(UUID groupId) {
        OrganizationGroup group = requireGroup(groupId);
        requireActiveCompany(group.getCompany().getId());
        if (group.getParent() != null && group.getParent().getStatus() == GroupStatus.ARCHIVED) {
            throw new InvalidStateException("Restore the parent Group before restoring this subtree");
        }
        setSubtreeStatus(group, GroupStatus.ACTIVE);
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.DELETE, description = "Delete empty organization group")
    public void deleteGroup(UUID groupId) {
        OrganizationGroup group = requireGroup(groupId);
        requireActiveCompany(group.getCompany().getId());
        long children = groupRepository.countByParentId(groupId);
        long memberships = membershipRepository.countByGroupId(groupId);
        List<String> blockers = new ArrayList<>();
        if (children > 0) blockers.add("childGroups: " + children);
        if (memberships > 0) blockers.add("memberPlacements: " + memberships);
        if (!blockers.isEmpty()) {
            throw new OperationBlockedException(
                    "Move or remove Group contents before deletion", blockers);
        }
        groupRepository.delete(group);
    }

    @Override
    @PermissionNode(key = OrganizationFeature.LIST_MEMBERSHIPS, description = "View Group memberships")
    public List<GroupMembershipDto> listMemberships(UUID groupId, boolean includeDescendants) {
        OrganizationGroup group = requireGroup(groupId);
        List<GroupMembership> memberships = includeDescendants
                ? membershipRepository.findAllByGroupIdIn(subtree(group).stream().map(OrganizationGroup::getId).toList())
                : membershipRepository.findAllByGroupId(groupId);
        return memberships.stream().map(this::toDto).toList();
    }

    @Override
    @PermissionNode(key = OrganizationFeature.LIST_MEMBER_PLACEMENTS, description = "View member Group placements")
    public List<GroupMembershipDto> listMemberPlacements(UUID companyId, UUID memberId) {
        Company company = requireCompany(companyId);
        memberRepository.findByIdAndAccountId(memberId, company.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        return membershipRepository.findAllByMemberIdAndGroupCompanyId(memberId, companyId).stream()
                .map(this::toDto).toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.PUT_MEMBERSHIP, description = "Add or update Group membership")
    public GroupMembershipDto putMembership(UUID groupId, UUID memberId, String positionTitle) {
        OrganizationGroup group = requireMutableGroup(groupId);
        var member = memberRepository.findByIdAndAccountId(memberId, group.getCompany().getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        GroupMembership membership = membershipRepository.findByGroupIdAndMemberId(groupId, memberId)
                .orElseGet(GroupMembership::new);
        membership.setGroup(group);
        membership.setMember(member);
        membership.setPositionTitle(normalizeOptional(positionTitle));
        return toDto(membershipRepository.save(membership));
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.REMOVE_MEMBERSHIP, description = "Remove Group membership")
    public void removeMembership(UUID groupId, UUID memberId) {
        OrganizationGroup group = requireMutableGroup(groupId);
        membershipRepository.findByGroupIdAndMemberId(groupId, memberId)
                .ifPresent(membershipRepository::delete);
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.CREATE_TEMPLATE, description = "Save Group structure template")
    public GroupTemplateDto createTemplate(UUID companyId, UUID sourceGroupId, GroupTemplateScope scope, String name) {
        Company company = requireActiveCompany(companyId);
        OrganizationGroup source = requireGroup(sourceGroupId);
        requireSameCompany(company, source);
        if (scope == GroupTemplateScope.PLATFORM) {
            throw new ForbiddenException("Platform Group templates require platform administration");
        }
        if (isB2bContext() && scope != GroupTemplateScope.COMPANY) {
            throw new ForbiddenException("B2B access cannot create Account-wide Group templates");
        }
        String displayName = normalizeRequiredName(name);
        String normalized = normalizeName(displayName);
        UUID ownerKey = scope == GroupTemplateScope.ACCOUNT ? company.getAccount().getId() : companyId;
        if (templateRepository.existsByScopeAndOwnerScopeKeyAndNormalizedName(scope, ownerKey, normalized)) {
            throw new DuplicateResourceException("GroupStructureTemplate", "name", displayName);
        }
        GroupStructureTemplate template = new GroupStructureTemplate();
        template.setScope(scope);
        template.setAccount(company.getAccount());
        template.setCompany(scope == GroupTemplateScope.COMPANY ? company : null);
        template.setName(displayName);
        template.setNormalizedName(normalized);
        template.setStatus(GroupStatus.ACTIVE);
        template = templateRepository.save(template);

        Map<UUID, GroupTemplateNode> copied = new HashMap<>();
        for (OrganizationGroup group : subtree(source)) {
            GroupTemplateNode node = new GroupTemplateNode();
            node.setTemplate(template);
            node.setParent(group.equals(source) ? null : copied.get(group.getParent().getId()));
            node.setName(group.getName());
            node.setNormalizedName(group.getNormalizedName());
            node.setDescription(group.getDescription());
            node.setDisplayOrder(group.getDisplayOrder());
            node.setPositionSuggestions(List.copyOf(group.getPositionSuggestions()));
            node = templateNodeRepository.save(node);
            copied.put(group.getId(), node);
        }
        return toDto(template, copied.size());
    }

    @Override
    @PermissionNode(key = OrganizationFeature.LIST_TEMPLATES, description = "View eligible Group structure templates")
    public List<GroupTemplateDto> listTemplates(UUID companyId) {
        Company company = requireCompany(companyId);
        List<GroupStructureTemplate> templates = new ArrayList<>();
        templates.addAll(templateRepository.findAllByScopeAndStatus(GroupTemplateScope.PLATFORM, GroupStatus.ACTIVE));
        templates.addAll(templateRepository.findAllByAccountIdAndScopeAndStatus(
                company.getAccount().getId(), GroupTemplateScope.ACCOUNT, GroupStatus.ACTIVE));
        templates.addAll(templateRepository.findAllByCompanyIdAndScopeAndStatus(
                companyId, GroupTemplateScope.COMPANY, GroupStatus.ACTIVE));
        return templates.stream().map(t -> toDto(t, templateNodeRepository
                .findAllByTemplateIdOrderByDisplayOrderAscNameAsc(t.getId()).size())).toList();
    }

    @Override
    @PermissionNode(key = OrganizationFeature.PREVIEW_TEMPLATE, description = "Preview Group template instantiation")
    public GroupTemplatePreviewDto previewTemplate(UUID templateId, UUID companyId, UUID parentId) {
        Company company = requireCompany(companyId);
        GroupStructureTemplate template = requireEligibleTemplate(templateId, company);
        OrganizationGroup parent = parentId == null ? null : requireGroup(parentId);
        requireSameCompany(company, parent);
        List<GroupTemplateNode> nodes = parentFirst(
                templateNodeRepository.findAllByTemplateIdOrderByDisplayOrderAscNameAsc(templateId));
        List<String> conflicts = new ArrayList<>();
        UUID targetScope = scopeKey(parent);
        nodes.stream().filter(node -> node.getParent() == null).forEach(root -> {
            if (groupRepository.existsByCompanyIdAndParentScopeKeyAndNormalizedName(
                    companyId, targetScope, root.getNormalizedName())) {
                conflicts.add("Sibling name already exists: " + root.getName());
            }
        });
        List<GroupTemplatePreviewDto.PreviewNode> previewNodes = nodes.stream().map(node ->
                new GroupTemplatePreviewDto.PreviewNode(
                        node.getId(), node.getParent() == null ? null : node.getParent().getId(),
                        node.getName(), node.getDescription(), node.getDisplayOrder(),
                        List.copyOf(node.getPositionSuggestions()))).toList();
        return new GroupTemplatePreviewDto(
                template.getId(), companyId, parentId, previewNodes, List.copyOf(conflicts), conflicts.isEmpty());
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.INSTANTIATE_TEMPLATE, description = "Instantiate Group structure template")
    public List<OrganizationGroupDto> instantiateTemplate(UUID templateId, UUID companyId, UUID parentId) {
        Company company = requireActiveCompany(companyId);
        GroupTemplatePreviewDto preview = previewTemplate(templateId, companyId, parentId);
        if (!preview.canInstantiate()) {
            throw new OperationBlockedException("Group template conflicts must be resolved before creation", preview.conflicts());
        }
        OrganizationGroup targetParent = parentId == null ? null : requireGroup(parentId);
        requireActiveParent(targetParent);
        List<GroupTemplateNode> nodes = templateNodeRepository.findAllByTemplateIdOrderByDisplayOrderAscNameAsc(templateId);
        Map<UUID, OrganizationGroup> created = new HashMap<>();
        int rootOrder = nextOrder(groupRepository.findAllByCompanyIdAndParentScopeKeyOrderByDisplayOrderAscNameAsc(
                companyId, scopeKey(targetParent)));
        List<OrganizationGroupDto> result = new ArrayList<>();
        for (GroupTemplateNode node : parentFirst(nodes)) {
            OrganizationGroup group = new OrganizationGroup();
            group.setCompany(company);
            group.setParent(node.getParent() == null ? targetParent : created.get(node.getParent().getId()));
            group.setName(node.getName());
            group.setNormalizedName(node.getNormalizedName());
            group.setDescription(node.getDescription());
            group.setDisplayOrder(node.getParent() == null ? rootOrder++ : node.getDisplayOrder());
            group.setStatus(GroupStatus.ACTIVE);
            group.setPositionSuggestions(List.copyOf(node.getPositionSuggestions()));
            group = groupRepository.save(group);
            created.put(node.getId(), group);
            result.add(toDto(group, 0L));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional
    @PermissionNode(key = OrganizationFeature.DELETE_TEMPLATE, description = "Delete custom Group structure template")
    public void deleteTemplate(UUID templateId) {
        GroupStructureTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupStructureTemplate", "id", templateId));
        UUID accountId = currentAccountId();
        if (template.getScope() == GroupTemplateScope.PLATFORM
                || template.getAccount() == null
                || !accountId.equals(template.getAccount().getId())) {
            throw new ForbiddenException("Group template cannot be deleted from this Account");
        }
        if (isB2bContext() && (template.getScope() != GroupTemplateScope.COMPANY
                || !HiveAppContextHolder.getContext().targetCompanyId().equals(template.getCompany().getId()))) {
            throw new ForbiddenException("B2B access cannot delete templates outside its target Company");
        }
        templateNodeRepository.detachParentsByTemplateId(templateId);
        templateNodeRepository.deleteAllByTemplateId(templateId);
        templateRepository.delete(template);
    }

    private Company requireCompany(UUID companyId) {
        requireB2bTargetCompany(companyId);
        return companyRepository.findByIdAndAccountId(companyId, currentAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
    }

    private Company requireActiveCompany(UUID companyId) {
        Company company = requireCompany(companyId);
        if (!company.isActive()) throw new InvalidStateException("Organization changes are unavailable while the Company is inactive");
        return company;
    }

    private OrganizationGroup requireGroup(UUID groupId) {
        OrganizationGroup group = groupRepository.findByIdAndCompanyAccountId(groupId, currentAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationGroup", "id", groupId));
        requireB2bTargetCompany(group.getCompany().getId());
        return group;
    }

    private OrganizationGroup requireMutableGroup(UUID groupId) {
        OrganizationGroup group = requireGroup(groupId);
        requireActiveCompany(group.getCompany().getId());
        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new InvalidStateException("Archived Groups are read-only until restored");
        }
        return group;
    }

    private void requireSameCompany(Company company, OrganizationGroup group) {
        if (group != null && !company.getId().equals(group.getCompany().getId())) {
            throw new ForbiddenException("Group belongs to a different Company");
        }
    }

    private void requireActiveParent(OrganizationGroup parent) {
        if (parent != null && parent.getStatus() == GroupStatus.ARCHIVED) {
            throw new InvalidStateException("New or moved Groups cannot be placed under an archived Group");
        }
    }

    private void rejectSiblingDuplicate(UUID companyId, UUID parentKey, String normalized, UUID excludedId) {
        boolean duplicate = excludedId == null
                ? groupRepository.existsByCompanyIdAndParentScopeKeyAndNormalizedName(companyId, parentKey, normalized)
                : groupRepository.existsByCompanyIdAndParentScopeKeyAndNormalizedNameAndIdNot(companyId, parentKey, normalized, excludedId);
        if (duplicate) throw new DuplicateResourceException("OrganizationGroup", "siblingName", normalized);
    }

    private List<OrganizationGroup> subtree(OrganizationGroup root) {
        List<OrganizationGroup> all = groupRepository.findAllByCompanyIdOrderByDisplayOrderAscNameAsc(root.getCompany().getId());
        Map<UUID, List<OrganizationGroup>> children = all.stream()
                .filter(group -> group.getParent() != null)
                .collect(Collectors.groupingBy(group -> group.getParent().getId()));
        List<OrganizationGroup> result = new ArrayList<>();
        Deque<OrganizationGroup> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            OrganizationGroup group = queue.removeFirst();
            result.add(group);
            queue.addAll(children.getOrDefault(group.getId(), List.of()));
        }
        return result;
    }

    private void setSubtreeStatus(OrganizationGroup group, GroupStatus status) {
        List<OrganizationGroup> groups = subtree(group);
        groups.forEach(item -> item.setStatus(status));
        groupRepository.saveAll(groups);
    }

    private GroupStructureTemplate requireEligibleTemplate(UUID templateId, Company company) {
        GroupStructureTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupStructureTemplate", "id", templateId));
        boolean eligible = template.getStatus() == GroupStatus.ACTIVE && switch (template.getScope()) {
            case PLATFORM -> true;
            case ACCOUNT -> template.getAccount().getId().equals(company.getAccount().getId());
            case COMPANY -> template.getCompany().getId().equals(company.getId());
        };
        if (!eligible) throw new ResourceNotFoundException("GroupStructureTemplate", "id", templateId);
        return template;
    }

    private List<GroupTemplateNode> parentFirst(List<GroupTemplateNode> nodes) {
        Map<UUID, List<GroupTemplateNode>> children = nodes.stream().filter(n -> n.getParent() != null)
                .collect(Collectors.groupingBy(n -> n.getParent().getId()));
        List<GroupTemplateNode> result = new ArrayList<>();
        Deque<GroupTemplateNode> queue = new ArrayDeque<>(nodes.stream().filter(n -> n.getParent() == null).toList());
        while (!queue.isEmpty()) {
            GroupTemplateNode node = queue.removeFirst();
            result.add(node);
            queue.addAll(children.getOrDefault(node.getId(), List.of()));
        }
        return result;
    }

    private OrganizationGroupDto toDto(OrganizationGroup group, long memberCount) {
        return new OrganizationGroupDto(
                group.getId(), group.getCompany().getId(), group.getParent() == null ? null : group.getParent().getId(),
                group.getName(), group.getDescription(), group.getDisplayOrder(), group.getStatus(),
                List.copyOf(group.getPositionSuggestions()), memberCount, group.getCreatedAt(), group.getUpdatedAt());
    }

    private GroupMembershipDto toDto(GroupMembership membership) {
        return new GroupMembershipDto(
                membership.getId(), membership.getGroup().getId(), membership.getMember().getId(),
                membership.getMember().getDisplayName(), membership.getPositionTitle());
    }

    private GroupTemplateDto toDto(GroupStructureTemplate template, int count) {
        return new GroupTemplateDto(
                template.getId(), template.getName(), template.getScope(), template.getStatus(),
                template.getAccount() == null ? null : template.getAccount().getId(),
                template.getCompany() == null ? null : template.getCompany().getId(), count);
    }

    private int nextOrder(List<OrganizationGroup> siblings) {
        return siblings.stream().mapToInt(OrganizationGroup::getDisplayOrder).max().orElse(-1) + 1;
    }

    private UUID scopeKey(OrganizationGroup parent) {
        return parent == null ? ROOT_SCOPE_KEY : parent.getId();
    }

    private String normalizeRequiredName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new InvalidRequestException("Group name is required");
        return normalized.replaceAll("\\s+", " ");
    }

    private String normalizeName(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> normalizePositions(List<String> positions) {
        if (positions == null) return List.of();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String position : positions) {
            String value = normalizeOptional(position);
            if (value != null) unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        }
        if (unique.size() > 50) throw new InvalidRequestException("A Group supports at most 50 position suggestions");
        return List.copyOf(unique.values());
    }

    private UUID currentAccountId() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.currentAccountId() == null) {
            throw new ForbiddenException("An active workspace context is required");
        }
        return context.currentAccountId();
    }

    private boolean isB2bContext() {
        var context = HiveAppContextHolder.getContext();
        return context != null && context.isB2B();
    }

    private void requireB2bTargetCompany(UUID companyId) {
        var context = HiveAppContextHolder.getContext();
        if (context != null && context.isB2B() && !companyId.equals(context.targetCompanyId())) {
            throw new ForbiddenException("B2B access is limited to the collaboration company");
        }
    }
}
