package com.tradefinder.api;

import lombok.Data;

/** {@code POST /session} response. The token lives in memory only; never write it to disk. */
@Data
public class SessionResponse
{
	private String token;
	private long playerId;
	private String rsn;
}
