package com.tradefinder.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * REST client for the Trade Finder backend. Every call is asynchronous ({@link Call#enqueue}) and
 * reports back on an OkHttp thread through {@link ApiCallback}. Pending calls are tracked so the
 * plugin can cancel them on shutdown. The bearer token is held in memory only.
 */
@Slf4j
public class TradeFinderApi
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final CacheControl NO_CACHE = new CacheControl.Builder().noCache().noStore().build();
	/** The one line for every call that got no answer at all (server down, no network). */
	public static final String UNREACHABLE = "Cannot reach the Trade Finder server.";
	private static final String STATE_DECLINED = "declined";
	private static final Type ORDER_LIST = new TypeToken<List<Order>>() {}.getType();
	private static final Type DEAL_LIST = new TypeToken<List<Deal>>() {}.getType();

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final Supplier<String> baseUrl;
	private final Set<Call> pending = ConcurrentHashMap.newKeySet();

	@Nullable
	private volatile String token;
	/** Makes "still wanted? then store the token" atomic against {@link #clearToken()}. */
	private final Object tokenLock = new Object();

	public TradeFinderApi(OkHttpClient okHttpClient, Gson gson, Supplier<String> baseUrl)
	{
		this.okHttpClient = okHttpClient;
		// Derived from the injected RuneLite Gson (never a fresh one): the backend speaks snake_case.
		this.gson = gson.newBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();
		this.baseUrl = baseUrl;
	}

	public boolean hasToken()
	{
		return token != null;
	}

	/** The session token, for the WebSocket in this package. */
	@Nullable
	String token()
	{
		return token;
	}

	/** The Gson derived for the backend (snake_case), shared with the WebSocket. */
	Gson gson()
	{
		return gson;
	}

	/** The configured base URL, or null when it does not parse. */
	@Nullable
	HttpUrl baseHttpUrl()
	{
		String text = baseUrl.get();
		return text == null ? null : HttpUrl.parse(text.trim());
	}

	/**
	 * {@code base} plus {@code path}, whether the base has no path, a trailing slash or a prefix
	 * such as {@code /api}. The socket uses the same helper; OkHttp opens an {@code https} URL as
	 * {@code wss}.
	 */
	static HttpUrl endpoint(HttpUrl base, String path)
	{
		return base.newBuilder().addPathSegments(path).build();
	}

	public void clearToken()
	{
		synchronized (tokenLock)
		{
			token = null;
		}
	}

	/** Cancel every in-flight call. Their callbacks are not invoked. */
	public void cancelAll()
	{
		for (Call call : pending)
		{
			call.cancel();
		}
		pending.clear();
	}

	/**
	 * {@code POST /session}: register this account and keep the returned token for later calls.
	 * The token is stored only while {@code stillWanted} is true when the reply arrives: a reply
	 * that comes after a logout or after the server was switched off must not revive the session
	 * (the callback still runs, and tells by the same check that it is stale).
	 */
	public void createSession(String accountHash, String rsn, BooleanSupplier stillWanted,
		ApiCallback<SessionResponse> callback)
	{
		JsonObject body = new JsonObject();
		body.addProperty("account_hash", accountHash);
		body.addProperty("rsn", rsn);
		send("session", null, "POST", body, SessionResponse.class, false, new ApiCallback<SessionResponse>()
		{
			@Override
			public void onSuccess(SessionResponse result)
			{
				synchronized (tokenLock)
				{
					if (stillWanted.getAsBoolean())
					{
						token = result.getToken();
					}
				}
				callback.onSuccess(result);
			}

			@Override
			public void onFailure(String message)
			{
				callback.onFailure(message);
			}
		});
	}

	/** {@code POST /orders}. The response carries a deal when the order matched at once. */
	public void postOrder(OrderRequest order, ApiCallback<PostOrderResponse> callback)
	{
		send("orders", null, "POST", gson.toJsonTree(order), PostOrderResponse.class, true, callback);
	}

	/** {@code DELETE /orders/{id}}. Cancelling a matched order cancels its deal too. */
	public void cancelOrder(long orderId, ApiCallback<Order> callback)
	{
		send("orders/" + orderId, null, "DELETE", null, Order.class, true, callback);
	}

	/** {@code GET /orders/me}. */
	public void myOrders(ApiCallback<List<Order>> callback)
	{
		send("orders/me", null, "GET", null, ORDER_LIST, true, callback);
	}

	/** {@code GET /deals/me}: the active deals of this player. */
	public void myDeals(ApiCallback<List<Deal>> callback)
	{
		send("deals/me", null, "GET", null, DEAL_LIST, true, callback);
	}

	/** {@code GET /me}: counters, order slots and the posting pause of this player. */
	public void me(ApiCallback<Me> callback)
	{
		send("me", null, "GET", null, Me.class, true, callback);
	}

	/** {@code GET /deals/history}: deals done, no-shows and the closed deals of this player, newest first. */
	public void history(ApiCallback<DealHistory> callback)
	{
		send("deals/history", null, "GET", null, DealHistory.class, true, callback);
	}

	/**
	 * {@code POST /deals/{id}/trade}: the trade screen with the partner opened, or closed without
	 * accepting. Both players send {@code declined} when the screen closes and the second one gets
	 * 409 because the deal is already back to {@code deal_on}; that counts as success and the
	 * callback then receives a null deal.
	 */
	public void tradeState(long dealId, String state, ApiCallback<Deal> callback)
	{
		JsonObject body = new JsonObject();
		body.addProperty("state", state);
		send("deals/" + dealId + "/trade", null, "POST", body, Deal.class, true, isAlreadyDeclined(state, callback));
	}

	static ApiCallback<Deal> isAlreadyDeclined(String state, ApiCallback<Deal> callback)
	{
		if (!STATE_DECLINED.equals(state))
		{
			return callback;
		}
		return new ApiCallback<Deal>()
		{
			@Override
			public void onSuccess(Deal result)
			{
				callback.onSuccess(result);
			}

			@Override
			public void onFailure(String message)
			{
				callback.onFailure(message);
			}

			@Override
			public void onFailure(int httpCode, String message)
			{
				if (httpCode == 409)
				{
					callback.onSuccess(null);
					return;
				}
				callback.onFailure(httpCode, message);
			}
		};
	}

	/**
	 * The last attempt at a trade report while the plugin shuts down: not tracked, so
	 * {@link #cancelAll()} does not cancel it, and nobody waits for the answer.
	 */
	public void reportAndForget(long dealId, TradeReport report)
	{
		send("deals/" + dealId + "/report", null, "POST", gson.toJsonTree(report), Deal.class, true, false,
			new ApiCallback<Deal>()
			{
				@Override
				public void onSuccess(Deal result)
				{
				}

				@Override
				public void onFailure(String message)
				{
					log.debug("Last trade report attempt failed: {}", message);
				}
			});
	}

	/**
	 * {@code POST /deals/{id}/seen} (no body): this player has the partner in view at the meeting
	 * spot. The server uses it when the deal expires: whoever was never seen loses their order.
	 */
	public void partnerSeen(long dealId, ApiCallback<JsonElement> callback)
	{
		send("deals/" + dealId + "/seen", null, "POST", null, JsonElement.class, true, callback);
	}

	/** {@code POST /deals/{id}/world-full}: the meeting world is full ({@code world} = the world shown on the card; a stale press gets the current card); the server answers 409 "No backup world left." at the end. */
	public void worldFull(long dealId, int world, ApiCallback<Deal> callback)
	{
		JsonObject body = new JsonObject();
		body.addProperty("world", world);
		send("deals/" + dealId + "/world-full", null, "POST", body, Deal.class, true, callback);
	}

	/** {@code POST /deals/{id}/report}: what this player gave and received in the accepted trade. */
	public void report(long dealId, TradeReport report, ApiCallback<Deal> callback)
	{
		send("deals/" + dealId + "/report", null, "POST", gson.toJsonTree(report), Deal.class, true, callback);
	}

	/**
	 * {@code GET /prices/{item_id}}, with {@code ?price=} when a limit price is given so the
	 * response says whether it is below, inside or above the deal zone.
	 */
	public void price(int itemId, @Nullable Long limitPrice, ApiCallback<PriceInfo> callback)
	{
		String query = limitPrice == null ? null : "price=" + limitPrice;
		send("prices/" + itemId, query, "GET", null, PriceInfo.class, true, callback);
	}

	private <T> void send(String path, @Nullable String query, String method, @Nullable Object jsonBody,
		Type responseType, boolean authenticated, ApiCallback<T> callback)
	{
		send(path, query, method, jsonBody, responseType, authenticated, true, callback);
	}

	private <T> void send(String path, @Nullable String query, String method, @Nullable Object jsonBody,
		Type responseType, boolean authenticated, boolean cancellable, ApiCallback<T> callback)
	{
		HttpUrl base = baseHttpUrl();
		if (base == null)
		{
			callback.onFailure("The Trade Finder server address is not valid.");
			return;
		}
		HttpUrl.Builder url = endpoint(base, path).newBuilder();
		if (query != null)
		{
			url.encodedQuery(query);
		}

		// Responses are private to this player: never read from or written to RuneLite's shared HTTP cache.
		Request.Builder request = new Request.Builder().url(url.build()).cacheControl(NO_CACHE);
		if (authenticated)
		{
			String current = token;
			if (current == null)
			{
				callback.onFailure("You are not connected to the Trade Finder server yet.");
				return;
			}
			request.header("Authorization", "Bearer " + current);
		}
		RequestBody body = jsonBody == null ? null : RequestBody.create(JSON, gson.toJson(jsonBody));
		if (body == null && "POST".equals(method))
		{
			body = RequestBody.create(JSON, "");
		}
		request.method(method, body);

		Call call = okHttpClient.newCall(request.build());
		if (cancellable)
		{
			pending.add(call);
		}
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				pending.remove(call);
				if (call.isCanceled())
				{
					return;
				}
				log.debug("Trade Finder request failed: {} {}", method, path, e);
				callback.onFailure(UNREACHABLE);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				pending.remove(call);
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody == null ? "" : responseBody.string();
					if (!response.isSuccessful())
					{
						callback.onFailure(response.code(), errorMessage(response.code(), text));
						return;
					}
					T value = gson.fromJson(text, responseType);
					callback.onSuccess(value);
				}
				catch (Exception e)
				{
					log.debug("Trade Finder response failed: {} {}", method, path, e);
					callback.onFailure("The server sent an answer the plugin could not read. Try again later.");
				}
			}
		});
	}

	/** True for the message of a call that never got an answer. */
	public static boolean isUnreachable(@Nullable String message)
	{
		return UNREACHABLE.equals(message);
	}

	/** FastAPI errors look like {@code {"detail": "..."}}; fall back to the status code. */
	private static String errorMessage(int code, String body)
	{
		try
		{
			JsonObject json = new JsonParser().parse(body).getAsJsonObject();
			if (json.has("detail") && json.get("detail").isJsonPrimitive())
			{
				return json.get("detail").getAsString();
			}
		}
		catch (Exception ignored)
		{
			// not JSON; use the status code
		}
		switch (code)
		{
			case 401:
				return "The server did not accept your session. Try again in a moment.";
			case 404:
				return "The server has nothing for that (any more).";
			default:
				return "The server had a problem (error " + code + "). Try again later.";
		}
	}
}
