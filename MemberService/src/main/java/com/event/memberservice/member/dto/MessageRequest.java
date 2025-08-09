package com.event.memberservice.member.dto;

import com.event.memberservice.member.repository.entity.MessageType;
import lombok.*;

@Getter
@RequiredArgsConstructor
@Builder
public class MessageRequest {

    private final String userId;
    private final String email;
    private final String contact;
    private final MessageType messageType;
}