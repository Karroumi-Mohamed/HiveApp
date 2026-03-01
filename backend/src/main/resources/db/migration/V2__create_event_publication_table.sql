CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    listener_id            VARCHAR(512)             NOT NULL,
    event_type             VARCHAR(512)             NOT NULL,
    serialized_event       VARCHAR(4000)            NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX event_publication_listener_serialized_idx
    ON event_publication (listener_id, serialized_event);

CREATE INDEX event_publication_completion_date_idx
    ON event_publication (completion_date);
