package com.event.memberservice.member.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberExitedEvent {
    private final String userId;
    private final String email;
}
