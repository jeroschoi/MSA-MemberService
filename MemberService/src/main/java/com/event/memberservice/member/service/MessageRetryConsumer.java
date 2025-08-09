package com.event.memberservice.member.service;

import com.event.memberservice.member.dto.MessageRequest;
import com.event.memberservice.member.event.MessageSendFailedEvent;
import com.event.memberservice.member.repository.entity.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRetryConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient webClient;
    private static final String FAILED_TOPIC = "message-failed-topic";
    private static final String DLT_TOPIC = "message-dlt-topic";
    private static final int MAX_RETRY = 3;

    @KafkaListener(topics = FAILED_TOPIC, groupId = "message-retry-service")
    public void retry(MessageSendFailedEvent event) {
        log.info("재시도 수신: userId={}, retryCount={}", event.getUserId(), event.getRetryCount());
        MessageRequest req = MessageRequest.builder()
                .userId(event.getUserId())
                .email(null) // 실제로라면 추가 데이터 필요 — 여기선 간단히
                .messageType(MessageType.PUSH)
                .build();

        webClient.post().uri(event.getEndpoint()).bodyValue(req)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(java.time.Duration.ofSeconds(3))
                .doOnError(error -> {
                    int next = event.getRetryCount() + 1;
                    log.warn("재시도 실패: userId={}, nextRetry={}", event.getUserId(), next);
                    if (next > MAX_RETRY) {
                        log.error("최대 재시도 초과, DLT로 이동: userId={}", event.getUserId());
                        kafkaTemplate.send(DLT_TOPIC, event.getUserId(), event);
                    } else {
                        kafkaTemplate.send(FAILED_TOPIC, event.getUserId(),
                                new MessageSendFailedEvent(event.getUserId(), event.getEndpoint(), error.getMessage(), next));
                    }
                })
                .doOnSuccess(v -> log.info("재시도 성공: userId={}", event.getUserId()))
                .subscribe();
    }
}