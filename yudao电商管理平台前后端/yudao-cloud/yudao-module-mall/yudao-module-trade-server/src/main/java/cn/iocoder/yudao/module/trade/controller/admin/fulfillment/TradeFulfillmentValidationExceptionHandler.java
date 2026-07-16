package cn.iocoder.yudao.module.trade.controller.admin.fulfillment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR;

/**
 * Value-free validation boundary for the fulfillment admin API.
 *
 * <p>Rejected logistics identifiers and manual audit text must not reach the global exception
 * logger. Controller method violations are client errors. Constraint violations originating
 * below the controller remain internal errors and are logged using code-owned metadata only.</p>
 */
@RestControllerAdvice(assignableTypes = TradeFulfillmentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TradeFulfillmentValidationExceptionHandler {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)*");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<?> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return badRequest(fieldNames(exception.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public CommonResult<?> handleBind(BindException exception) {
        return badRequest(fieldNames(exception.getBindingResult()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public CommonResult<?> handleMissingRequestHeader() {
        return badRequest(Set.of("Idempotency-Key"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public CommonResult<?> handleUnreadableMessage() {
        return badRequest(Set.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<?> handleConstraintViolation(ConstraintViolationException exception) {
        if (isControllerMethodValidation(exception.getConstraintViolations())) {
            return badRequest(parameterNames(exception.getConstraintViolations()));
        }
        Set<String> rootClasses = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getRootBeanClass)
                .map(type -> type == null ? "unknown" : type.getName())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> propertyPaths = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(TradeFulfillmentValidationExceptionHandler::safePath)
                .collect(Collectors.toCollection(TreeSet::new));
        log.error("[handleInternalConstraintViolation][rootClasses({}) propertyPaths({})]",
                rootClasses, propertyPaths);
        return CommonResult.error(INTERNAL_SERVER_ERROR);
    }

    private static boolean isControllerMethodValidation(
            Set<? extends ConstraintViolation<?>> violations) {
        return !violations.isEmpty() && violations.stream().allMatch(violation -> {
            Class<?> rootClass = violation.getRootBeanClass();
            if (rootClass == null || !TradeFulfillmentController.class.isAssignableFrom(rootClass)) {
                return false;
            }
            boolean methodNode = false;
            boolean parameterNode = false;
            for (Path.Node node : violation.getPropertyPath()) {
                methodNode |= node.getKind() == ElementKind.METHOD;
                parameterNode |= node.getKind() == ElementKind.PARAMETER;
            }
            return methodNode && parameterNode;
        });
    }

    private static Set<String> fieldNames(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> safeIdentifier(error.getField()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> parameterNames(Set<? extends ConstraintViolation<?>> violations) {
        Set<String> names = new LinkedHashSet<>();
        for (ConstraintViolation<?> violation : violations) {
            for (Path.Node node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.PARAMETER) {
                    names.add(safeIdentifier(node.getName()));
                }
            }
        }
        return names;
    }

    private static String safePath(Path path) {
        if (path == null) {
            return "unknown";
        }
        StringBuilder result = new StringBuilder();
        for (Path.Node node : path) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(node.getKind().name()).append(':').append(safeIdentifier(node.getName()));
        }
        return result.length() == 0 ? "unknown" : result.toString();
    }

    private static String safeIdentifier(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches() ? value : "unknown";
    }

    private static CommonResult<?> badRequest(Collection<String> fields) {
        String message = fields.isEmpty() ? BAD_REQUEST.getMsg()
                : BAD_REQUEST.getMsg() + ":" + String.join(",", fields);
        return CommonResult.error(BAD_REQUEST.getCode(), message);
    }
}
