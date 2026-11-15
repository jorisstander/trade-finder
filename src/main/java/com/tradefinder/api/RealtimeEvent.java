package com.tradefinder.api;

import javax.annotation.Nullable;
import lombok.Data;

/**
 * One message pushed over the WebSocket. {@code type} is {@code deal.matched}, {@code deal.cancelled},
 * {@code deal.expired}, {@code deal.completed}, {@code deal.disputed}, {@code deal.trade_declined},
 * {@code deal.world_changed} (all with {@code deal_id} and the {@code deal} card), {@code deal.partner_nearby} ({@code deal_id}
 * only), {@code heartbeat.ack} or {@code error} ({@code detail}).
 */
@Data
public class RealtimeEvent
{
	public static final String MATCHED = "deal.matched";
	public static final String CANCELLED = "deal.cancelled";
	public static final String EXPIRED = "deal.expired";
	public static final String COMPLETED = "deal.completed";
	public static final String DISPUTED = "deal.disputed";
	public static final String TRADE_DECLINED = "deal.trade_declined";
	/** The deal moved to the next backup world; {@code deal} carries the updated card. */
	public static final String WORLD_CHANGED = "deal.world_changed";
	public static final String PARTNER_NEARBY = "deal.partner_nearby";
	public static final String HEARTBEAT_ACK = "heartbeat.ack";
	public static final String ERROR = "error";

	private String type;
	private long dealId;
	@Nullable
	private Deal deal;
	@Nullable
	private String detail;
}
