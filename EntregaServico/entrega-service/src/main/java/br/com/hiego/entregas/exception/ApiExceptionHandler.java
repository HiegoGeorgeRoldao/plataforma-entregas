package br.com.hiego.entregas.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(EntregaNaoEncontradaException.class)
    ProblemDetail naoEncontrada(EntregaNaoEncontradaException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Verifique os campos informados");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ErroCampo(error.getField(), error.getDefaultMessage())).toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private record ErroCampo(String field, String message) {}
}
