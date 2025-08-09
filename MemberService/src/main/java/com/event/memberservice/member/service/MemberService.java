package com.event.memberservice.member.service;

import com.event.memberservice.member.dto.MemberJoinRequest;
import com.event.memberservice.member.dto.MemberResponse;
import com.event.memberservice.member.dto.MessageRequest;
import com.event.memberservice.member.event.MemberExitedEvent;
import com.event.memberservice.member.event.MemberJoinCompensateEvent;
import com.event.memberservice.member.event.MessageSendFailedEvent;
import com.event.memberservice.member.exception.MemberErrorCode;
import com.event.memberservice.member.exception.MemberException;
import com.event.memberservice.member.mapper.MemberMapper;
import com.event.memberservice.member.repository.MemberRepository;
import com.event.memberservice.member.repository.entity.MemberEntity;
import com.event.memberservice.member.event.MemberJoinedEvent;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberMapper memberMapper;
    // WebClient.Builder 대신, 미리 생성된 WebClient Bean을 직접 주입받습니다.
    private final WebClient messageWebClient;

    private final ApplicationEventPublisher eventPublisher;

    private static final int MESSAGE_API_TIMEOUT_SECONDS = 3;
    private static final int MESSAGE_API_MAX_RETRY_COUNT = 2;

    // @Value 설정은 WebClientConfig로 이동했으므로 여기서 제거합니다.

    /**
     * 회원가입 로직
     */
    @Transactional
    public MemberResponse join(MemberJoinRequest request) {
        validateDuplicateMember(request);

        MemberEntity memberEntity = memberMapper.toEntity(request);
        memberEntity.setPassword(passwordEncoder.encode(request.getPassword()));

        try {
            MemberEntity savedMember = memberRepository.save(memberEntity);
            // 메시지 API 호출 실패는 회원가입 로직의 성공/실패에 영향을 주지 않습니다.
            // 비동기로 호출하고, 에러 발생 시 로그만 기록합니다.
            callMessageApi("/send-join", savedMember)
                    .subscribe(null, error -> log.error("회원가입 메시지 전송 실패 (비동기): {}", error.getMessage()));

            eventPublisher.publishEvent(new MemberJoinedEvent(savedMember.getUserId(), savedMember.getEmail()));

            return memberMapper.toResponse(savedMember);
        } catch (DataIntegrityViolationException e) {
            log.error("회원가입 DB 제약조건 위배: userId={}", request.getUserId(), e);

            eventPublisher.publishEvent(new MemberJoinCompensateEvent(request.getUserId(), "중복 데이터로 인한 롤백"));

            throw new MemberException(MemberErrorCode.DUPLICATE_USER_ID, "데이터 저장 중 중복이 발생했습니다.");
        } catch (Exception e) {
            // 일반 예외 발생 시에도 보상 이벤트 발행 가능
            eventPublisher.publishEvent(new MemberJoinCompensateEvent(request.getUserId(), "기타 예외로 인한 롤백"));
            throw e;
        }
    }

    /**
     * 회원가입 시 중복 검사
     */
    private void validateDuplicateMember(MemberJoinRequest request) {
        if (memberRepository.existsByUserId(request.getUserId())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_USER_ID);
        }
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByContact(request.getContact())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_CONTACT);
        }
    }

    /**
     * 회원탈퇴 로직
     */
    @Transactional
    public void exit(String userId) {
        MemberEntity memberEntity = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (!memberEntity.isActive()) {
            throw new MemberException(MemberErrorCode.ALREADY_EXITED);
        }

        memberEntity.setActive(false);
        memberEntity.setExitDate(LocalDateTime.now());

        // DB 저장 (필요시 repository.save 호출 또는 JPA 영속성 컨텍스트에 맡김)
        memberRepository.save(memberEntity);

        callMessageApi("/send-exit", memberEntity)
                .subscribe(null, error -> log.error("회원탈퇴 메시지 전송 실패 (비동기): {}", error.getMessage()));

        eventPublisher.publishEvent(new MemberExitedEvent(memberEntity.getUserId(), memberEntity.getEmail()));
    }

    /**
     * 메시지 서비스 API 호출 (비동기)
     * 주입받은 messageWebClient를 사용하여 API를 호출합니다.
     */
    private Mono<Void> callMessageApi(String endpoint, MemberEntity memberEntity) {
        MessageRequest requestDto = memberMapper.toMessageRequest(memberEntity);
        return messageWebClient // 미리 생성된 WebClient 인스턴스를 사용합니다.
                .post()
                .uri(endpoint) // baseUrl이 이미 설정되어 있으므로, 상세 경로만 지정합니다.
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(MESSAGE_API_TIMEOUT_SECONDS)) // 3초 타임아웃
                .retry(MESSAGE_API_MAX_RETRY_COUNT) // 실패 시 2번 재시도
                .doOnError(error -> {
                    log.error("메시지 API 호출 실패: userId={}, error={}", memberEntity.getUserId(), error.getMessage());
                    eventPublisher.publishEvent(
                            new MessageSendFailedEvent(memberEntity.getUserId(), endpoint, error.getMessage(), 0));
                });
    }

    @Transactional(readOnly = true)
    public MemberResponse findByUserId(String userId) {
        return memberRepository.findByUserId(userId)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MemberResponse findByContact(String contact) {
        return memberRepository.findByContact(contact)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<MemberResponse> findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .map(memberMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getAllActiveMembers() {
        List<MemberEntity> activeMembers = memberRepository.findByActiveTrue();
        return memberMapper.toActiveResponseList(activeMembers);
    }
}
