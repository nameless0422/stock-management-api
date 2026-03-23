package com.stockmanagement.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(6) WHERE id = ?")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 회원 탈퇴 시각 — null이면 활성 계정, NOT NULL이면 탈퇴 계정 */
    @Column
    private LocalDateTime deletedAt;

    @Builder
    private User(String username, String password, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role != null ? role : UserRole.USER;
    }

    /** 관리자가 사용자 권한을 변경한다. */
    public void changeRole(UserRole role) {
        this.role = role;
    }

    /** 회원 탈퇴 처리 — @SQLDelete가 DELETE → UPDATE로 변환하므로 직접 호출 불필요. JPA 표준 delete()와 함께 사용. */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 탈퇴 여부를 반환한다. */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
