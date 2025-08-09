package com.event.memberservice.member.service;

import com.event.memberservice.member.dto.MemberJoinRequest;
import com.event.memberservice.member.dto.MemberResponse;
import com.event.memberservice.member.exception.MemberErrorCode;
import com.event.memberservice.member.exception.MemberException;
import com.event.memberservice.member.mapper.MemberMapper;
import com.event.memberservice.member.repository.MemberRepository;
import com.event.memberservice.member.repository.entity.MemberEntity;
import com.event.memberservice.member.repository.entity.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WebClient messageWebClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // --- 테스트 데이터 ---
    private MemberJoinRequest joinRequest;
    private MemberEntity memberEntity;
    private MemberResponse memberResponse;

    @BeforeEach
    void setUp() {
        // 테스트에 필요한 공통 데이터 설정
        joinRequest = MemberJoinRequest.builder()
                .userId("testuser")
                .password("password123!")
                .name("테스트유저")
                .email("test@example.com")
                .contact("010-1234-5678")
                .messageType(MessageType.SMS)
                .build();

        memberEntity = MemberEntity.builder()
                .id(1L)
                .userId("testuser")
                .password("encodedPassword")
                .name("테스트유저")
                .email("test@example.com")
                .contact("010-1234-5678")
                .messageType(MessageType.SMS)
                .joinDate(LocalDateTime.now())
                .active(true)
                .build();

        memberResponse = MemberResponse.builder()
                .id(1L)
                .userId("testuser")
                .build();
    }

    private void mockWebClient() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(messageWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("회원가입 테스트")
    class JoinTest {

        @Test
        @DisplayName("성공")
        void join_Success() {
            // given
            when(memberRepository.existsByUserId(anyString())).thenReturn(false);
            when(memberRepository.existsByEmail(anyString())).thenReturn(false);
            when(memberRepository.existsByContact(anyString())).thenReturn(false);
            when(memberMapper.toEntity(joinRequest)).thenReturn(memberEntity);
            when(passwordEncoder.encode(joinRequest.getPassword())).thenReturn("encodedPassword");
            when(memberRepository.save(memberEntity)).thenReturn(memberEntity);
            when(memberMapper.toResponse(memberEntity)).thenReturn(memberResponse);
            mockWebClient();

            // when
            MemberResponse result = memberService.join(joinRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("testuser");

            // verify
            verify(memberRepository).existsByUserId(joinRequest.getUserId());
            verify(memberRepository).existsByEmail(joinRequest.getEmail());
            verify(memberRepository).existsByContact(joinRequest.getContact());
            verify(memberRepository).save(any(MemberEntity.class));
            verify(messageWebClient, times(1)).post();
        }

        @Test
        @DisplayName("실패 - 중복된 사용자 ID")
        void join_Fail_DuplicateUserId() {
            // given
            when(memberRepository.existsByUserId(anyString())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> memberService.join(joinRequest))
                    .isInstanceOf(MemberException.class)
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.DUPLICATE_USER_ID);

            // verify
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 중복된 이메일")
        void join_Fail_DuplicateEmail() {
            // given
            when(memberRepository.existsByUserId(anyString())).thenReturn(false);
            when(memberRepository.existsByEmail(anyString())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> memberService.join(joinRequest))
                    .isInstanceOf(MemberException.class)
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.DUPLICATE_EMAIL);

            // verify
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 중복된 연락처")
        void join_Fail_DuplicateContact() {
            // given
            when(memberRepository.existsByUserId(anyString())).thenReturn(false);
            when(memberRepository.existsByEmail(anyString())).thenReturn(false);
            when(memberRepository.existsByContact(anyString())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> memberService.join(joinRequest))
                    .isInstanceOf(MemberException.class)
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.DUPLICATE_CONTACT);

            // verify
            verify(memberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("회원 조회 테스트")
    class FindTest {

        @Test
        @DisplayName("ID로 조회 성공")
        void findByUserId_Success() {
            // given
            when(memberRepository.findByUserId("testuser")).thenReturn(Optional.of(memberEntity));
            when(memberMapper.toResponse(memberEntity)).thenReturn(memberResponse);

            // when
            MemberResponse result = memberService.findByUserId("testuser");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("ID로 조회 실패 - 존재하지 않는 회원")
        void findByUserId_Fail_NotFound() {
            // given
            when(memberRepository.findByUserId("nonexistent")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> memberService.findByUserId("nonexistent"))
                    .isInstanceOf(MemberException.class)
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("회원 탈퇴 테스트")
    class ExitTest {

        @Test
        @DisplayName("성공")
        void exit_Success() {
            // given
            when(memberRepository.findByUserId("testuser")).thenReturn(Optional.of(memberEntity));
            mockWebClient();

            // when
            memberService.exit("testuser");

            // then
            assertThat(memberEntity.isActive()).isFalse();
            assertThat(memberEntity.getExitDate()).isNotNull();
            verify(memberRepository).findByUserId("testuser");
            verify(messageWebClient, times(1)).post();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 회원")
        void exit_Fail_MemberNotFound() {
            // given
            when(memberRepository.findByUserId("nonexistent")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> memberService.exit("nonexistent"))
                    .isInstanceOf(MemberException.class)
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("활성 회원 전체 목록 조회 성공")
    void getAllActiveMembers_Success() {
        // given
        List<MemberEntity> memberEntities = List.of(memberEntity);
        List<MemberResponse> memberResponses = List.of(memberResponse);
        when(memberRepository.findByActiveTrue()).thenReturn(memberEntities);
        when(memberMapper.toActiveResponseList(memberEntities)).thenReturn(memberResponses);

        // when
        List<MemberResponse> results = memberService.getAllActiveMembers();

        // then
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo("testuser");
    }
}
