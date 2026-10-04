package com.coachingcenter.api.purchases;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@RestController
public class PurchaseController {

	private final PurchaseService purchases;

	private final PermissionChecker permissions;

	public PurchaseController(PurchaseService purchases, PermissionChecker permissions) {
		this.purchases = purchases;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/courses/{id}/purchases")
	public ResponseEntity<PurchaseService.CoursePurchaseResult> purchaseCourse(@PathVariable UUID id,
			@RequestBody JsonNode body) {
		requirePurchase();
		return ResponseEntity.status(HttpStatus.CREATED).body(purchases.purchaseCourse(id, actor().userId(), body));
	}

	@PostMapping("/api/v1/books/{id}/orders")
	public ResponseEntity<PurchaseService.BookOrderResult> placeOrder(@PathVariable UUID id, @RequestBody JsonNode body) {
		requirePurchase();
		return ResponseEntity.status(HttpStatus.CREATED).body(purchases.placeBookOrder(id, actor().userId(), body));
	}

	@GetMapping("/api/v1/me/purchases")
	public List<PurchaseService.CoursePurchaseView> myPurchases() {
		requirePurchase();
		return purchases.listMyPurchases(actor().userId());
	}

	@GetMapping("/api/v1/me/orders")
	public List<PurchaseService.BookOrderView> myOrders() {
		requirePurchase();
		return purchases.listMyOrders(actor().userId());
	}

	private void requirePurchase() {
		if (!permissions.has(Permissions.PURCHASE_CREATE, true)) {
			throw denied();
		}
	}

	private CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
			.getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

}
