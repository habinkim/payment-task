-- 지갑 충전 이력
-- charge_no 는 호출자가 생성하며 멱등 키다. 결제(uk_payment_no)와 같은 원리다.
-- status 컬럼이 없는 이유: 충전은 외부 승인을 거치지 않아 중간 상태가 존재하지 않는다.
-- 성공하면 행이 있고 실패하면 트랜잭션이 롤백되어 행이 없다.
CREATE TABLE wallet_charge
(
    id         BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    charge_no  VARCHAR(64)    NOT NULL,
    wallet_id  BIGINT         NOT NULL,
    amount     DECIMAL(19, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    created_at TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_wallet_charge_no UNIQUE (charge_no),
    CONSTRAINT ck_wallet_charge_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_wallet_charge_wallet_id ON wallet_charge (wallet_id);
