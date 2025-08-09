package com.event.memberservice.member.service;

import com.event.memberservice.member.event.MemberJoinedEvent;
import com.event.memberservice.member.event.MemberJoinCompensateEvent;
import com.event.memberservice.member.event.MemberExitedEvent;
import com.event.memberservice.member.event.MemberExitCompensateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;

import static org.mockito.Mockito.*;

class MemberEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private MemberEventPublisher eventPublisher;

    private static final String MEMBER_TOPIC = "member-joined-topic";
    private static final String MEMBER_COMPENSATE_TOPIC = "member-rollback-topic";
    private static final String MEMBER_EXIT_COMPENSATE_TOPIC = "member-exit-rollback-topic";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void onMemberJoined_ShouldSendToKafkaAfterCommit() {
        MemberJoinedEvent event = new MemberJoinedEvent("user123", "user@example.com");

        eventPublisher.onMemberJoined(event);

        verify(kafkaTemplate, times(1)).send(eq(MEMBER_TOPIC), eq("user123"), eq(event));
    }

    @Test
    void onMemberJoinRollback_ShouldSendCompensateEventAfterRollback() {
        MemberJoinedEvent event = new MemberJoinedEvent("user123", "user@example.com");

        eventPublisher.onMemberJoinRollback(event);

        ArgumentCaptor<MemberJoinCompensateEvent> captor = ArgumentCaptor.forClass(MemberJoinCompensateEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(MEMBER_COMPENSATE_TOPIC), eq("user123"), captor.capture());

        MemberJoinCompensateEvent compensateEvent = captor.getValue();
        assert(compensateEvent.getUserId().equals("user123"));
        assert(compensateEvent.getReason().equals("Transaction Rolled Back"));
    }

    // 회원 탈퇴 이벤트 테스트 예시
    @Test
    void onMemberExited_ShouldSendToKafkaAfterCommit() {
        MemberExitedEvent event = new MemberExitedEvent("user123", "user@example.com");

        eventPublisher.onMemberExited(event);

        // assuming MEMBER_EXIT_TOPIC is defined in MemberEventPublisher, adjust accordingly
        verify(kafkaTemplate, times(1)).send(eq("member-exited-topic"), eq("user123"), eq(event));
    }

    @Test
    void onMemberExitRollback_ShouldSendExitCompensateEventAfterRollback() {
        MemberExitedEvent event = new MemberExitedEvent("user123", "user@example.com");

        eventPublisher.onMemberExitRollback(event);

        ArgumentCaptor<MemberExitCompensateEvent> captor = ArgumentCaptor.forClass(MemberExitCompensateEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(MEMBER_EXIT_COMPENSATE_TOPIC), eq("user123"), captor.capture());

        MemberExitCompensateEvent compensateEvent = captor.getValue();
        assert(compensateEvent.getUserId().equals("user123"));
        assert(compensateEvent.getReason().equals("Transaction Rolled Back"));
    }
}