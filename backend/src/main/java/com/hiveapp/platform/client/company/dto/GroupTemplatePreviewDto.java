package com.hiveapp.platform.client.company.dto;

import java.util.List;
import java.util.UUID;

public record GroupTemplatePreviewDto(
        UUID templateId,
        UUID targetCompanyId,
        UUID targetParentId,
        List<PreviewNode> nodes,
        List<String> conflicts,
        boolean canInstantiate
) {
    public record PreviewNode(
            UUID templateNodeId,
            UUID parentTemplateNodeId,
            String name,
            String description,
            int displayOrder,
            List<String> positionSuggestions
    ) {}
}
