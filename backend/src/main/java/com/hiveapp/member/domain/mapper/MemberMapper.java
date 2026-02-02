package com.hiveapp.member.domain.mapper;

import com.hiveapp.member.domain.dto.MemberResponse;
import com.hiveapp.member.domain.dto.MemberRoleResponse;
import com.hiveapp.member.domain.entity.Member;
import com.hiveapp.member.domain.entity.MemberRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "roles", source = "memberRoles")
    MemberResponse toResponse(Member member);

    List<MemberResponse> toResponseList(List<Member> members);

    @Mapping(target = "accountWide", expression = "java(memberRole.isAccountWide())")
    MemberRoleResponse toRoleResponse(MemberRole memberRole);
}
