package com.hiveapp.shared.email;

public interface EmailService {

    /**
     * Sends a workspace invitation email to the invitee.
     *
     * @param to           invitee email address
     * @param inviterName  display name of the person who sent the invite
     * @param workspaceName name of the workspace being invited into
     * @param acceptUrl    full URL the invitee clicks to accept (includes token)
     */
    void sendInvitation(String to, String inviterName, String workspaceName, String acceptUrl);
}
