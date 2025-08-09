package com.event.memberservice.member.event;

import lombok.*;

@Getter @AllArgsConstructor
public class MemberJoinedEvent {
    private final String userId;
    private final String email;
}