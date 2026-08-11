-- 평가자가 클론 직후 결제를 호출할 수 있도록 지갑을 미리 넣는다.
-- 인메모리 DB라 재기동하면 사라지므로 시드가 마이그레이션에 포함되어야 한다.
INSERT INTO wallet (id, currency, balance, created_at, updated_at) VALUES
    (1, 'USD', 1000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'USD',   10.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'JPY', 50000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
