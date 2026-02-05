package com.thock.back.member.app;


import com.thock.back.global.eventPublisher.EventPublisher;
import com.thock.back.global.exception.CustomException;
import com.thock.back.global.exception.ErrorCode;
import com.thock.back.member.domain.entity.Member;
import com.thock.back.member.out.MemberRepository;
import com.thock.back.shared.member.domain.MemberRole;
import com.thock.back.shared.member.event.MemberModifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberUpdateService {

    private final MemberRepository memberRepository;
    private final EventPublisher eventPublisher;

    public void updateMemberRole(Long memberId, String bankCode, String accountNumber, String accountHolder) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String memberRole = member.getRole().toString();

        if (member.getRole() == MemberRole.USER) {
            member.setRole(MemberRole.SELLER);
            member.setBankCode(bankCode);
            member.setAccountNumber(accountNumber);
            member.setAccountHolder(accountHolder);
            memberRepository.save(member);

            eventPublisher.publish(new MemberModifiedEvent(member.toDto()));
        } else {
            throw new CustomException(ErrorCode.INVALID_ROLE_PROMOTION);
        }
    }
}
