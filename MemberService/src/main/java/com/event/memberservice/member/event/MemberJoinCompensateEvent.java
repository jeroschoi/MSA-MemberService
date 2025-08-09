package com.event.memberservice.member.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberJoinCompensateEvent {
    private final String userId;
    private final String reason;
}
