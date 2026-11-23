CREATE TABLE ecofood.user_access (
 user_id UUID PRIMARY KEY,
 is_admin BOOLEAN NOT NULL DEFAULT FALSE,
 admin_email_hash VARCHAR(64),
 suspended BOOLEAN NOT NULL DEFAULT FALSE,
 rate_window TIMESTAMP WITH TIME ZONE,
 rate_count INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE ecofood.admin_audit (
 id UUID PRIMARY KEY, actor_id UUID, target_id UUID NOT NULL,
 action VARCHAR(50) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reason VARCHAR(500) NOT NULL, previous_value VARCHAR(40), new_value VARCHAR(40)
);
CREATE INDEX admin_audit_time ON ecofood.admin_audit(occurred_at,id);
CREATE TABLE ecofood.catalog_events (
 id UUID PRIMARY KEY, target_id UUID NOT NULL, event_type VARCHAR(40) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX catalog_events_time ON ecofood.catalog_events(occurred_at,event_type);
CREATE TABLE ecofood.reporting_metadata (id INTEGER PRIMARY KEY, activity_tracking_since TIMESTAMP WITH TIME ZONE NOT NULL);
INSERT INTO ecofood.reporting_metadata VALUES (1,CURRENT_TIMESTAMP);
