package com.tradefinder.api;

import javax.annotation.Nullable;
import lombok.Data;

/** {@code POST /orders} response: the new order, plus the deal when it matched at once. */
@Data
public class PostOrderResponse
{
	private Order order;
	@Nullable
	private Deal deal;
}
