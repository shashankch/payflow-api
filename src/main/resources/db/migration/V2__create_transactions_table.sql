CREATE TABLE transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    reference_id UUID NOT NULL UNIQUE,
    sender_id BIGINT NOT NULL REFERENCES users(user_id),
    receiver_id BIGINT NOT NULL REFERENCES users(user_id),
    sender_upi_id VARCHAR(100) NOT NULL,
    receiver_upi_id VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
