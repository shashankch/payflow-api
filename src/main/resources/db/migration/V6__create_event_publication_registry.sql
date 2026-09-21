-- V6__create_event_publication_registry.sql
-- Spring Modulith Transactional Outbox Event Publication Registry

CREATE TABLE IF NOT EXISTS event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255),
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_event_pub_completion ON event_publication (completion_date);
CREATE INDEX IF NOT EXISTS idx_event_pub_date ON event_publication (publication_date);

CREATE TABLE IF NOT EXISTS event_publication_archive (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255),
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_event_pub_arch_completion ON event_publication_archive (completion_date);
CREATE INDEX IF NOT EXISTS idx_event_pub_arch_date ON event_publication_archive (publication_date);
