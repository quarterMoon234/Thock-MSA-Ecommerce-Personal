package com.thock.back.member.domain.service;

import com.thock.back.global.exception.CustomException;
import com.thock.back.global.exception.ErrorCode;
import com.thock.back.member.domain.command.LoginCommand;
import com.thock.back.member.domain.entity.Credential;
import com.thock.back.member.domain.entity.Member;
import com.thock.back.member.out.CredentialRepository;
import com.thock.back.member.out.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

//  왜 Domain Service인가?
//  - 인증은 핵심 도메인 로직
//  - Member와 Credential 두 엔티티에 걸쳐있음
//  - 외부 시스템 의존 없음 (Repository는 Port)
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberAuthenticator {

    private final MemberRepository memberRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    public Member authenticate(LoginCommand command) {
        Member member = findMemberByEmail(command.email());
        validateMemberState(member);
        validateCredential(member.getId(), command.password());

        log.info("[Auth] Member authenticated successfully. memberId={}, email={}",
                member.getId(), member.getEmail());

        return member;
    }

    // Email로 회원 조회 및 존재 여부 검증
    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[AUTH] Login attempt with non-existent email: {}", email);
                    return new CustomException(ErrorCode.INVALID_CREDENTIALS);
                });
    }

    // 계정 상태 검증
    private void validateMemberState(Member member) {
        if (member.isWithdrawn()) {
            log.warn("[AUTH] Withdrawn member login attempt. memberId={}", member.getId());
            throw new CustomException(ErrorCode.MEMBER_WITHDRAWN);
        }

        if (member.isInActive()) {
            log.warn("[AUTH] Inactive member login attempt. memberId={}", member.getId());
            throw new CustomException(ErrorCode.MEMBER_INACTIVE);
        }
    }

    private void validateCredential(Long memberId, String password) {
        Credential credential = credentialRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CREDENTIAL_NOT_FOUND));

        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            log.warn("[AUTH] Invalid password attempt. memberId={}", memberId);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }
}
