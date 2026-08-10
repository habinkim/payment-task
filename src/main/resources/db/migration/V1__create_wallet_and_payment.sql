CREATE TABLE wallet
(
    id         BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    currency   CHAR(3)        NOT NULL COMMENT 'ISO 4217 통화 코드',
    balance    DECIMAL(19, 4) NOT NULL COMMENT '금액 정밀도 보장을 위해 DECIMAL 사용',
    created_at TIMESTAMP(6)   NOT NULL,
    updated_at TIMESTAMP(6)   NOT NULL,
    CONSTRAINT ck_wallet_balance_non_negative CHECK (balance >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payment
(
    id                      BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_no              VARCHAR(64)    NOT NULL COMMENT '3rd party가 생성. 조회 키이자 멱등 키',
    wallet_id               BIGINT         NOT NULL,
    amount                  DECIMAL(19, 4) NOT NULL,
    currency                CHAR(3)        NOT NULL,
    status                  VARCHAR(16)    NOT NULL COMMENT 'PENDING/COMPLETED/FAILED/UNKNOWN',
    failure_reason          VARCHAR(32)     NULL COMMENT '실패 시에만 채워진다',
    retriable               BOOLEAN         NULL COMMENT '실패가 재시도 가능한지. CS 안내 기준',
    external_transaction_id VARCHAR(64)     NULL COMMENT 'PG사 대조 문의용',
    external_response_code  VARCHAR(32)     NULL COMMENT 'PG사 대조 문의용',
    requested_at            TIMESTAMP(6)    NULL COMMENT '외부 호출 시작 시각',
    responded_at            TIMESTAMP(6)    NULL COMMENT '외부 응답 수신 시각',
    created_at              TIMESTAMP(6)   NOT NULL,
    updated_at              TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_payment_no UNIQUE (payment_no),
    CONSTRAINT ck_payment_amount_positive CHECK (amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_status_created_at ON payment (status, created_at);
CREATE INDEX idx_payment_wallet_id ON payment (wallet_id);
