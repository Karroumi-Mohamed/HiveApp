package com.hiveapp.platform.client.account.mapper;

import org.mapstruct.Mapper;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.dto.AccountDto;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    @Mapping(source = "active", target = "isActive")
    AccountDto toDto(Account account);
}
