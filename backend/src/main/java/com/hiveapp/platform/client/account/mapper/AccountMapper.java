package com.hiveapp.platform.client.account.mapper;

import org.mapstruct.Mapper;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.dto.AccountDto;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountDto toDto(Account account);
}
