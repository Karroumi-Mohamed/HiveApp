package com.hiveapp.platform.client.collaboration.dto;

public record CollaborationInitiationResult(
        CollaborationDto collaboration,
        Outcome outcome
) {
    public enum Outcome {
        CREATED_INITIAL,
        EXISTING_IDENTICAL,
        CREATED_AFTER_TERMINAL
    }
}
