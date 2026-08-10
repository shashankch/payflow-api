CREATE UNIQUE INDEX idx_users_upi_id ON users(upi_id);
CREATE UNIQUE INDEX idx_users_reference_id ON users(reference_id);
CREATE INDEX idx_tx_sender_created ON transactions(sender_upi_id, created_at DESC);
CREATE INDEX idx_tx_receiver_created ON transactions(receiver_upi_id, created_at DESC);
CREATE INDEX idx_tx_reference_id ON transactions(reference_id);
CREATE INDEX idx_ledger_user_created ON balance_ledger(user_id, created_at DESC);
