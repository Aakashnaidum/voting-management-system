package io.github.aakashnaidum.voting.service;

/** A rule violation that should be shown to the user (not a server error). */
public class VotingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public VotingException(String message) {
        super(message);
    }
}
