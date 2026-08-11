-- 지갑
-- balance 는 금액 정밀도 보장을 위해 DECIMAL 을 쓴다. DOUBLE 은 이진 부동소수라 원장에 쓸 수 없다.
CREATE TABLE wallet
(
    id         BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    currency   VARCHAR(3)     NOT NULL,
    balance    DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP(6)   NOT NULL,
    updated_at TIMESTAMP(6)   NOT NULL,
    CONSTRAINT ck_wallet_balance_non_negative CHECK (balance >= 0)
);

-- 결제 원장
-- payment_no 는 3rd party 가 생성하며 조회 키이자 멱등 키다(docs/adr/0002).
-- external_transaction_id, external_response_code, retriable 은 명세에 없는 컬럼이다.
-- 실패 이력을 CS 문의 대응에 쓰려면 게이트웨이 대조와 재시도 안내가 가능해야 한다.
CREATE TABLE payment
(
    id                      BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_no              VARCHAR(64)    NOT NULL,
    wallet_id               BIGINT         NOT NULL,
    amount                  DECIMAL(19, 4) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    status                  VARCHAR(16)    NOT NULL,
    failure_reason          VARCHAR(32)     NULL,
    retriable               BOOLEAN         NULL,
    external_transaction_id VARCHAR(64)     NULL,
    external_response_code  VARCHAR(32)     NULL,
    requested_at            TIMESTAMP(6)    NULL,
    responded_at            TIMESTAMP(6)    NULL,
    created_at              TIMESTAMP(6)   NOT NULL,
    updated_at              TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_payment_no UNIQUE (payment_no),
    CONSTRAINT ck_payment_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payment_status_created_at ON payment (status, created_at);
CREATE INDEX idx_payment_wallet_id ON payment (wallet_id);
