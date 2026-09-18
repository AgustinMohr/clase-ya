package com.claseya.common.exception;

/**
 * Thrown when two referenced entities cannot be associated because they belong
 * to different parents (e.g. a career from another university). Mapped to 400.
 */
public class InvalidAssociationException extends RuntimeException {

    public InvalidAssociationException(String message) {
        super(message);
    }
}
