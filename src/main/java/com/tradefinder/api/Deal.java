package com.tradefinder.api;

import javax.annotation.Nullable;
import lombok.Data;

/** The deal card the backend sends: the same shape for {@code GET /deals/{id}}, {@code GET /deals/me} and pushes. */
@Data
public class Deal
{
	private long id;
	private int itemId;
	/** What this deal trades: the smaller remaining amount of the two orders. */
	private long quantity;
	private long price;
	private int world;
	private String meetSpot;
	/** {@code deal_on}, {@code trading}, {@code completed}, {@code disputed}, {@code expired} or {@code cancelled}. */
	private String status;
	private String createdAt;
	private String expiresAt;
	/** From this time on cancelling is free (the partner did not show up); null when the server sends none. */
	@Nullable
	private String freeCancelAt;
	private Side mySide;
	private long myOrderId;
	private String partnerRsn;
	/** The partner's lifetime counters, for the deal card. */
	private Reputation partner;
}
