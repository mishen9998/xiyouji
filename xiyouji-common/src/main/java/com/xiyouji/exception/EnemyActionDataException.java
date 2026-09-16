package com.xiyouji.exception;

/** Marker survives a JPA converter exception chain so the API can report content errors. */
public class EnemyActionDataException extends IllegalArgumentException {
    public EnemyActionDataException(String message, Throwable cause) { super(message, cause); }
}
