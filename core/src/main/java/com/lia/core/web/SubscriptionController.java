package com.lia.core.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.subscription.SubscriptionUseCase;
import com.lia.core.auth.CurrentUser;
import com.lia.core.web.SubscriptionApiDtos.SubscriptionItem;
import com.lia.core.web.SubscriptionApiDtos.SubscriptionListResponse;
import com.lia.core.web.SubscriptionApiDtos.SubscriptionRequest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;

/** {@code /api/v1/subscriptions} — 법령 구독(D74, [[service-api-spec]] §3.6). HTTP 경계만, 404·409·400·401 은 ApiExceptionHandler. */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionUseCase subscriptions;

    public SubscriptionController(SubscriptionUseCase subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    @Operation(operationId = "listSubscriptions")
    public SubscriptionListResponse list(HttpServletRequest request) {
        return new SubscriptionListResponse(
                subscriptions.list(CurrentUser.require(request)).stream().map(SubscriptionItem::from).toList());
    }

    /** 만들거나 시점 변경 — 204. */
    @PutMapping("/{lawId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "putSubscription")
    public ResponseEntity<Void> put(@PathVariable String lawId, @RequestBody SubscriptionRequest body,
                                    HttpServletRequest request) {
        subscriptions.subscribe(CurrentUser.require(request), lawId, body == null ? null : body.notifyBefore());
        return ResponseEntity.noContent().build();
    }

    /** 멱등 해지 — 204. */
    @DeleteMapping("/{lawId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteSubscription")
    public ResponseEntity<Void> delete(@PathVariable String lawId, HttpServletRequest request) {
        subscriptions.unsubscribe(CurrentUser.require(request), lawId);
        return ResponseEntity.noContent().build();
    }
}
