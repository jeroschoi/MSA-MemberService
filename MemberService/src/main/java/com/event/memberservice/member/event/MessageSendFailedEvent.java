package com.event.memberservice.member.event;

import lombok.*;

@Getter @AllArgsConstructor
public class MessageSendFailedEvent {
    private final String userId;
    private final String endpoint;
    private final String errorMessage;
    private final int retryCount; // 재시도 카운트
}