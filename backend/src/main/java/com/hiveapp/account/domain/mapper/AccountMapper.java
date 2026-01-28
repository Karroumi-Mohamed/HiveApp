package com.hiveapp.account.domain.mapper;

import com.hiveapp.account.domain.dto.AccountResponse;
import com.hiveapp.account.domain.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "active", source = "active")
    AccountResponse toResponse(Account account);

    List<AccountResponse> toResponseList(List<Account> accounts);
}
