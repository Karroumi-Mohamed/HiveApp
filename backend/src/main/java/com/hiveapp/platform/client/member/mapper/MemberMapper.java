package com.hiveapp.platform.client.member.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.dto.MemberDto;

@Mapper(componentModel = "spring")
public interface MemberMapper {
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "owner",  target = "isOwner")
    @Mapping(source = "active", target = "isActive")
    MemberDto toDto(Member member);
}
