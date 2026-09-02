package com.xiyouji.exception;

import com.xiyouji.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessAndStorageErrors() {
        assertStatus(handler.handleBusinessException(new InvalidActionException("bad")), HttpStatus.BAD_REQUEST);
        assertStatus(handler.handleStorageUnavailable(
                new StorageUnavailableException("redis down", new IllegalStateException())),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsValidationErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("request", "name", "required")));
        assertStatus(handler.handleMethodArgumentNotValidException(
                new MethodArgumentNotValidException(null, bindingResult)), HttpStatus.BAD_REQUEST);
        assertStatus(handler.handleConstraintViolationException(new ConstraintViolationException(Set.of())),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsMalformedAndMissingParameterErrors() {
        HttpMessageNotReadableException malformed = new HttpMessageNotReadableException(
                "invalid json", mock(HttpInputMessage.class));
        assertStatus(handler.handleHttpMessageNotReadableException(malformed), HttpStatus.BAD_REQUEST);
        assertStatus(handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("roomCode", "String")), HttpStatus.BAD_REQUEST);
        assertStatus(handler.handleMethodArgumentTypeMismatchException(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsAccessNotFoundAndUnexpectedErrors() {
        assertStatus(handler.handleAccessDeniedException(new AccessDeniedException("denied")), HttpStatus.FORBIDDEN);
        assertStatus(handler.handleNoHandlerFoundException(
                new NoHandlerFoundException("/missing", "GET", new HttpHeaders())), HttpStatus.NOT_FOUND);
        assertStatus(handler.handleRuntimeException(new IllegalStateException("boom")), HttpStatus.INTERNAL_SERVER_ERROR);
        assertStatus(handler.handleException(new Exception("boom")), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void assertStatus(org.springframework.http.ResponseEntity<ErrorResponse> response, HttpStatus expected) {
        assertEquals(expected.value(), response.getStatusCode().value());
    }
}
