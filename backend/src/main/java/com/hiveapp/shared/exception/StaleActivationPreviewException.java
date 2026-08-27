package com.hiveapp.shared.exception;

/**
 * The signed activation evidence no longer describes the catalogue state being applied.
 *
 * <p>This is deliberately distinct from an entity optimistic-lock conflict: callers must request
 * a fresh activation preview instead of merely reloading the edited product row.</p>
 */
public class StaleActivationPreviewException extends RuntimeException {

    public StaleActivationPreviewException() {
        super("Activation preview is no longer current. Request a fresh preview and retry.");
    }
}
