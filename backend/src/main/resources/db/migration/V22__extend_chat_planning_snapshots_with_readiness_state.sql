ALTER TABLE chat_planning_snapshots
    ALTER COLUMN snapshot_json DROP NOT NULL;

ALTER TABLE chat_planning_snapshots
    ADD COLUMN last_evaluated_message_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE chat_planning_snapshots
    ADD COLUMN planning_status VARCHAR(40) NOT NULL DEFAULT 'DISCOVERY_IN_PROGRESS';

ALTER TABLE chat_planning_snapshots
    ADD COLUMN planning_status_reason TEXT;
