package com.tubadev.receivables.infrastructure.configuration;

import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.exceptions.InternalErrorException;
import com.tubadev.receivables.domain.exceptions.NotFoundException;
import com.tubadev.receivables.domain.validation.Error;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record ErrorResponse(String message, List<Error> errors) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException ex, final HttpHeaders headers, final HttpStatusCode status, final WebRequest request) {
        final var errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e instanceof FieldError fe ? new Error(fe.getField(), fe.getDefaultMessage()) : new Error(e.getDefaultMessage()))
                .toList();
        return ResponseEntity.unprocessableContent().body(new ErrorResponse("Invalid request", errors));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(final NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage(), ex.getErrors()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(final DomainException ex) {
        return ResponseEntity.unprocessableContent().body(new ErrorResponse(ex.getMessage(), ex.getErrors()));
    }

    @ExceptionHandler(InternalErrorException.class)
    public ResponseEntity<ErrorResponse> handleInternalError(final InternalErrorException ex) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(ex.getMessage(), List.of()));
    }
}
