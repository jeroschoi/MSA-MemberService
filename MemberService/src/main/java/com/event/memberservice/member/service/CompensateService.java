package com.event.memberservice.member.service;

import com.event.memberservice.member.repository.MemberRepository;
import com.event.memberservice.member.event.MemberJoinCompensateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensateService {

    private final MemberRepository memberRepository;

    @Transactional
    @KafkaListener(topics = "member-join-compensate-topic", groupId = "compensate-service")
    public void handleMemberJoinCompensate(MemberJoinCompensateEvent event) {
        log.info("보상 이벤트 처리 시작: userId={}, reason={}", event.getUserId(), event.getReason());

        try {
            memberRepository.findByUserId(event.getUserId()).ifPresent(member -> {
                if (!member.isActive()) {
                    log.info("이미 비활성화 된 회원, 보상 처리 생략: userId={}", event.getUserId());
                    return; // 멱등성 보장 (중복 처리 방지)
                }
                member.setActive(false);
                member.setExitDate(LocalDateTime.now());
                memberRepository.save(member);
                log.info("회원 보상 처리 완료: userId={}", event.getUserId());
            });
        } catch (Exception e) {
            log.error("보상 이벤트 처리 중 오류 발생: userId={}, error={}", event.getUserId(), e.getMessage(), e);
        }
    }
}