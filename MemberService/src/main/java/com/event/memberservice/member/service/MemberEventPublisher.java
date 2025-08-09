package com.event.memberservice.member.service;

import com.event.memberservice.member.event.MemberExitCompensateEvent;
import com.event.memberservice.member.event.MemberExitedEvent;
import com.event.memberservice.member.event.MemberJoinCompensateEvent;
import com.event.memberservice.member.event.MemberJoinedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MemberEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String MEMBER_TOPIC = "member-joined-topic";
    private static final String MEMBER_COMPENSATE_TOPIC = "member-rollback-topic";

    private static final String MEMBER_EXITED_TOPIC = "member-exited-topic";
    private static final String MEMBER_EXIT_COMPENSATE_TOPIC = "member-exit-rollback-topic";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(MemberJoinedEvent event) {
        // 트랜잭션 커밋 성공 후 호출됨
        kafkaTemplate.send(MEMBER_TOPIC, event.getUserId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onMemberJoinRollback(MemberJoinedEvent event) {
        // 롤백 시점에 보상 이벤트 생성 및 발행
        MemberJoinCompensateEvent compensateEvent =
                new MemberJoinCompensateEvent(event.getUserId(), "Transaction Rolled Back");
        kafkaTemplate.send(MEMBER_COMPENSATE_TOPIC, event.getUserId(), compensateEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberExited(MemberExitedEvent event) {
        kafkaTemplate.send(MEMBER_EXITED_TOPIC, event.getUserId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onMemberExitRollback(MemberExitedEvent event) {
        MemberExitCompensateEvent compensateEvent =
                new MemberExitCompensateEvent(event.getUserId(), "Transaction Rolled Back");
        kafkaTemplate.send(MEMBER_EXIT_COMPENSATE_TOPIC, event.getUserId(), compensateEvent);
    }

}
