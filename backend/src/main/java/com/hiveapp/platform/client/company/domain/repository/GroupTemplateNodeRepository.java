package com.hiveapp.platform.client.company.domain.repository;

import com.hiveapp.platform.client.company.domain.entity.GroupTemplateNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupTemplateNodeRepository extends JpaRepository<GroupTemplateNode, UUID> {
    List<GroupTemplateNode> findAllByTemplateIdOrderByDisplayOrderAscNameAsc(UUID templateId);

    @Modifying
    @Query("UPDATE GroupTemplateNode node SET node.parent = null WHERE node.template.id = :templateId")
    void detachParentsByTemplateId(@Param("templateId") UUID templateId);

    @Modifying
    @Query("DELETE FROM GroupTemplateNode node WHERE node.template.id = :templateId")
    void deleteAllByTemplateId(@Param("templateId") UUID templateId);
}
