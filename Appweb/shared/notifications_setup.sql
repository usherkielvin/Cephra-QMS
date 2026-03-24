-- ============================================================
-- Cephra QMS: Notifications event bus
-- Run in MySQL Workbench: open this file, select all, execute (⚡)
-- Target database: cephradb
-- ============================================================

USE cephradb;

-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_type    VARCHAR(60)  NOT NULL,
    payload       JSON         NOT NULL,
    processed     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_processed_created (processed, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Drop existing triggers before recreating
DROP TRIGGER IF EXISTS trg_ticket_status_changed;
DROP TRIGGER IF EXISTS trg_bay_status_changed;
DROP TRIGGER IF EXISTS trg_ticket_inserted;

-- Trigger: queue_tickets status change
CREATE TRIGGER trg_ticket_status_changed
AFTER UPDATE ON queue_tickets
FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        INSERT INTO notifications (event_type, payload)
        VALUES (
            'ticket_status_changed',
            JSON_OBJECT(
                'ticket_id',  NEW.ticket_id,
                'username',   NEW.username,
                'old_status', OLD.status,
                'new_status', NEW.status,
                'bay_number', NEW.bay_number
            )
        );
    END IF;
END;

-- Trigger: charging_bays status change
CREATE TRIGGER trg_bay_status_changed
AFTER UPDATE ON charging_bays
FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        INSERT INTO notifications (event_type, payload)
        VALUES (
            'bay_status_changed',
            JSON_OBJECT(
                'bay_number',        NEW.bay_number,
                'old_status',        OLD.status,
                'new_status',        NEW.status,
                'current_username',  NEW.current_username,
                'current_ticket_id', NEW.current_ticket_id
            )
        );
    END IF;
END;

-- Trigger: new queue ticket inserted
CREATE TRIGGER trg_ticket_inserted
AFTER INSERT ON queue_tickets
FOR EACH ROW
BEGIN
    INSERT INTO notifications (event_type, payload)
    VALUES (
        'ticket_created',
        JSON_OBJECT(
            'ticket_id',    NEW.ticket_id,
            'username',     NEW.username,
            'service_type', NEW.service_type,
            'status',       NEW.status
        )
    );
END;
