-- PostgreSQL 16: communication v1 -> scoped one-way notifications.
-- Apply to a backed-up existing HiveApp schema while old app/worker instances are stopped.
-- This is an explicit migration, not a replacement for the platform's earlier schema migrations.
-- Run with psql -v ON_ERROR_STOP=1; the transaction rolls back on any incompatible existing data.
BEGIN;
SELECT pg_advisory_xact_lock(48291022922026);

CREATE TABLE IF NOT EXISTS communication_publications (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0, kind varchar(20) NOT NULL, purpose varchar(20) NOT NULL,
 message_title varchar(160) NOT NULL, message_body varchar(10000) NOT NULL, account_ids jsonb NOT NULL,
 email boolean NOT NULL DEFAULT false, replies boolean NOT NULL DEFAULT false,
 available_at timestamptz NOT NULL, expires_at timestamptz, state varchar(20) NOT NULL,
 actor_id uuid NOT NULL, reason varchar(2000), offer_id uuid
);
ALTER TABLE communication_publications ADD COLUMN IF NOT EXISTS offer_id uuid;

CREATE TABLE IF NOT EXISTS communication_entries (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0, account_id uuid, publication_id uuid,
 source varchar(40) NOT NULL, source_id uuid NOT NULL, kind varchar(20) NOT NULL, purpose varchar(20) NOT NULL,
 message_title varchar(160) NOT NULL, message_body varchar(10000) NOT NULL, required_permission varchar(255), action_path varchar(255),
 available_at timestamptz NOT NULL, expires_at timestamptz,
 cancelled boolean NOT NULL DEFAULT false, hidden boolean NOT NULL DEFAULT false,
 replies boolean NOT NULL DEFAULT false, closed boolean NOT NULL DEFAULT false, last_reply_at timestamptz,
 notice_created_at timestamptz, notice_delivery varchar(24), notice_email_attempts integer,
 notice_email_claim_id uuid, notice_email_claimed_at timestamptz, notice_email_recipient_id uuid,
 CONSTRAINT uk_communication_source UNIQUE(source,source_id,account_id)
);
ALTER TABLE communication_entries ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS audience varchar(20) NOT NULL DEFAULT 'ACCOUNT';
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS recipient_user_id uuid;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS company_id uuid;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS event_id uuid;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS event_type varchar(100);
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS resource_id uuid;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS topic varchar(24) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS resolved_at timestamptz;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS next_email_attempt_at timestamptz;
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS optional boolean NOT NULL DEFAULT false;

-- Remove only old enum CHECKs involving kind; preserve unrelated constraints.
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conrelid::regclass AS tbl, conname FROM pg_constraint
  WHERE contype='c' AND conrelid IN ('communication_entries'::regclass,'communication_publications'::regclass)
  AND pg_get_constraintdef(oid) ~ '\mkind\M'
 LOOP EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',c.tbl,c.conname); END LOOP;
END $$;
ALTER TABLE communication_entries ADD CONSTRAINT ck_communication_entry_kind CHECK(kind IN ('NOTICE','WARNING','ACTION','OFFER','MESSAGE'));
ALTER TABLE communication_publications ADD CONSTRAINT ck_communication_publication_kind CHECK(kind IN ('NOTICE','WARNING','ACTION','OFFER','MESSAGE'));
UPDATE communication_entries SET kind='NOTICE',replies=false,closed=true WHERE kind='MESSAGE';
UPDATE communication_publications SET kind='NOTICE',replies=false WHERE kind='MESSAGE';
UPDATE communication_entries SET topic='COMMERCIAL' WHERE source='PLAN_CONTENT' AND topic='GENERAL';
UPDATE communication_entries SET topic='BILLING' WHERE source='REPRICING' AND topic='GENERAL';
UPDATE communication_entries SET optional=true WHERE source='ADMIN' AND kind='NOTICE';

CREATE TABLE IF NOT EXISTS communication_interactions (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 entry_id uuid NOT NULL, user_id uuid NOT NULL, acknowledged_at timestamptz, archived_at timestamptz,
 CONSTRAINT uk_communication_interaction UNIQUE(entry_id,user_id)
);
CREATE TABLE IF NOT EXISTS communication_preferences (
 account_id uuid PRIMARY KEY, marketing_in_app boolean NOT NULL DEFAULT false, marketing_email boolean NOT NULL DEFAULT false
);
-- Historical replies are retained for an explicitly approved retention policy, never served by the new API.
CREATE TABLE IF NOT EXISTS communication_replies (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 entry_id uuid NOT NULL, actor_id uuid NOT NULL, command_id uuid NOT NULL,
 from_admin boolean NOT NULL DEFAULT false, reply_body varchar(4000) NOT NULL,
 CONSTRAINT uk_communication_reply_command UNIQUE(entry_id,actor_id,command_id)
);
CREATE TABLE IF NOT EXISTS notification_events (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 dedupe_key varchar(64) NOT NULL UNIQUE, definition_key varchar(100) NOT NULL, audience varchar(20) NOT NULL,
 account_id uuid, recipient_user_id uuid, company_id uuid, resource_id uuid,
 message_title varchar(160) NOT NULL, message_body varchar(10000) NOT NULL, email boolean NOT NULL DEFAULT false,
 available_at timestamptz NOT NULL, expires_at timestamptz, resolved_at timestamptz,
 state varchar(20) NOT NULL, attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL, failure_code varchar(120),
 CONSTRAINT ck_notification_event_state CHECK(state IN ('PENDING','DELIVERED','FAILED'))
);
CREATE TABLE IF NOT EXISTS notification_send_commands (
 command_key varchar(110) PRIMARY KEY, payload_hash varchar(64) NOT NULL, created_at timestamptz NOT NULL
);
ALTER TABLE notification_events ADD COLUMN IF NOT EXISTS cancelled boolean NOT NULL DEFAULT false;
ALTER TABLE notification_events ADD COLUMN IF NOT EXISTS sender_user_id uuid;
ALTER TABLE notification_events ADD COLUMN IF NOT EXISTS sender_name varchar(255);
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS sender_name varchar(255);
ALTER TABLE communication_entries ADD COLUMN IF NOT EXISTS email_failure_code varchar(80);
ALTER TABLE notification_send_commands ADD COLUMN IF NOT EXISTS command_id uuid;
ALTER TABLE notification_send_commands ADD COLUMN IF NOT EXISTS account_id uuid;
ALTER TABLE notification_send_commands ADD COLUMN IF NOT EXISTS sender_user_id uuid;
ALTER TABLE notification_send_commands ADD COLUMN IF NOT EXISTS message_title varchar(160);
ALTER TABLE notification_send_commands ADD COLUMN IF NOT EXISTS message_body varchar(10000);
ALTER TABLE notification_send_commands ADD COLUMN IF NOT EXISTS recipients integer NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_notification_sent ON notification_send_commands(account_id,sender_user_id,created_at,command_key);
CREATE INDEX IF NOT EXISTS idx_notification_sent_events ON notification_events(account_id,sender_user_id,resource_id,state);
CREATE TABLE IF NOT EXISTS notification_preferences (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 user_id uuid NOT NULL, topic varchar(24) NOT NULL, in_app_enabled boolean NOT NULL DEFAULT true, email_enabled boolean NOT NULL DEFAULT true,
 CONSTRAINT uk_notification_preference UNIQUE(user_id,topic)
);
DO $$ DECLARE table_name text; BEGIN
 FOREACH table_name IN ARRAY ARRAY['communication_entries','notification_events'] LOOP
  IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid=table_name::regclass AND conname='ck_'||table_name||'_audience') THEN
   EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I CHECK (
    (audience=''ACCOUNT'' AND account_id IS NOT NULL AND recipient_user_id IS NULL) OR
    (audience=''MEMBER'' AND account_id IS NOT NULL AND recipient_user_id IS NOT NULL) OR
    (audience=''PLATFORM'' AND account_id IS NULL AND recipient_user_id IS NULL AND company_id IS NULL) OR
    (audience=''OPERATOR'' AND account_id IS NULL AND recipient_user_id IS NOT NULL AND company_id IS NULL))',table_name,'ck_'||table_name||'_audience');
  END IF;
 END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uk_communication_event ON communication_entries(event_id);
CREATE INDEX IF NOT EXISTS idx_communication_inbox ON communication_entries(account_id,available_at,id);
CREATE INDEX IF NOT EXISTS idx_communication_publication ON communication_entries(publication_id,id);
CREATE INDEX IF NOT EXISTS idx_communication_delivery ON communication_entries(notice_delivery,available_at,id);
CREATE INDEX IF NOT EXISTS idx_communication_personal ON communication_entries(audience,recipient_user_id,account_id,available_at,id);
CREATE INDEX IF NOT EXISTS idx_communication_resource ON communication_entries(event_type,resource_id);
CREATE INDEX IF NOT EXISTS idx_communication_thread ON communication_replies(entry_id,created_at,id);
CREATE INDEX IF NOT EXISTS idx_notification_event_due ON notification_events(state,next_attempt_at,id);
CREATE INDEX IF NOT EXISTS idx_notification_event_resource ON notification_events(definition_key,resource_id);

-- Idempotency keys/history are not purged by this upgrade. No business grants or source data change.
COMMIT;
