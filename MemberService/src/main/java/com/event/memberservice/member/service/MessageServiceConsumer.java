package com.event.memberservice.member.service;

import com.event.memberservice.member.dto.MessageRequest;
import com.event.memberservice.member.event.MemberJoinedEvent;
import com.event.memberservice.member.event.MessageSendFailedEvent;
import com.event.memberservice.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.event.memberservice.member.repository.entity.MessageType;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient webClient;
    private static final String FAILED_TOPIC = "message-failed-topic";

    @KafkaListener(topics = "member-joined-topic", groupId = "message-service")
    public void handleMemberJoined(MemberJoinedEvent event) {
        log.info("MessageServiceConsumer 수신: {}", event.getUserId());
        MessageRequest req = MessageRequest.builder()
                .userId(event.getUserId())
                .email(event.getEmail())
                .messageType(MessageType.PUSH)
                .build();

        // 비동기 호출 (블로킹 없이)
        webClient.post()
                .uri("/send-join")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(java.time.Duration.ofSeconds(3))
                .doOnError(error -> {
                    log.error("메시지 전송 실패: userId={}, error={}", event.getUserId(), error.getMessage());
                    // 실패 이벤트 발행 (초기 retryCount = 0)
                    kafkaTemplate.send(FAILED_TOPIC, event.getUserId(),
                            new MessageSendFailedEvent(event.getUserId(), "/send-join", error.getMessage(), 0));
                })
                .doOnSuccess(v -> log.info("메시지 전송 성공: userId={}", event.getUserId()))
                .subscribe(); // 구독해서 실행
    }
}