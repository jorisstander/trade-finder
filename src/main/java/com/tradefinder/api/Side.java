package com.tradefinder.api;

import com.google.gson.annotations.SerializedName;

public enum Side
{
	@SerializedName("buy")
	BUY("Buy"),
	@SerializedName("sell")
	SELL("Sell");

	private final String label;

	Side(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
