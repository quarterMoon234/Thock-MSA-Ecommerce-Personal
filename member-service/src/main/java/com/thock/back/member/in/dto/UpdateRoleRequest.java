package com.thock.back.member.in.dto;

public record UpdateRoleRequest(
        String bankCode,
        String accountNumber,
        String accountHolder
) {}
