CREATE TABLE event_publication (
    id               UUID         NOT NULL,
    listener_id      VARCHAR(255) NOT NULL,
    event_type       VARCHAR(255) NOT NULL,
    serialized_event TEXT         NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);
