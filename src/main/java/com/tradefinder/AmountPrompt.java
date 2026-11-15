package com.tradefinder;

import com.tradefinder.api.Deal;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * One extra clickable line in the chatbox "Enter amount:" prompt while the trade window with the
 * deal partner is open: "Trade Finder: 4,500 (click to fill)". A click writes the missing amount
 * into the prompt's input, exactly as if typed; the player still presses Enter. Nothing is filled
 * without the click, nothing is submitted, no menu entry is touched and it only exists inside this
 * numeric prompt (never the chat input). Same pattern as the Plugin Hub's Grand Exchange offer
 * editors: a text widget in the prompt layer with an op listener, removed when the prompt closes.
 * Client thread only. The amount itself is {@link TradeNeeds}.
 */
class AmountPrompt
{
	/**
	 * {@code VarClientID.MESLAYERMODE} while the chatbox shows a numeric prompt ("Enter amount:").
	 * gameval has no names for the mode values.
	 */
	static final int MESLAYERMODE_NUMBER = 7;
	private static final String PROMPT_TITLE = "enter amount";
	private static final String OFFER_X = "Offer-X";
	private static final String REMOVE_X = "Remove-X";
	/** No Offer-X seen for this prompt: assume the thing the player owes. */
	private static final int UNKNOWN_ITEM = -1;
	/** The prompt belongs to taking something out of the offer: no line. */
	private static final int NOT_AN_OFFER = -2;
	private static final int LINE_HEIGHT = 16;
	private static final int LINE_GAP = 6;
	private static final int LINE_COLOR = 0xFF981F;

	private final Client client;
	private final ItemManager itemManager;
	private final TradeTracker tracker;

	/** The canonical item the player chose Offer-X on, or one of the markers above. */
	private int offerItemId = UNKNOWN_ITEM;
	@Nullable
	private Widget line;
	/** The line is on screen for the prompt that is open now. */
	private boolean shown;
	private boolean promptWasOpen;

	AmountPrompt(Client client, ItemManager itemManager, TradeTracker tracker)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.tracker = tracker;
	}

	/** Remember which item an "Offer-X" belongs to; the prompt that opens next is for it. */
	void onMenuOptionClicked(String option, int itemId)
	{
		if (OFFER_X.equals(option))
		{
			offerItemId = itemManager.canonicalize(itemId);
		}
		else if (REMOVE_X.equals(option))
		{
			offerItemId = NOT_AN_OFFER;
		}
	}

	/** Every client tick, but cheap: two comparisons unless a trade with the partner is open. */
	void onClientTick()
	{
		boolean open = tracker.canOffer() && promptOpen();
		if (!open)
		{
			hide();
			if (promptWasOpen)
			{
				// The prompt is over; the next Offer-X tells which item the next one is for.
				promptWasOpen = false;
				offerItemId = UNKNOWN_ITEM;
			}
			return;
		}
		promptWasOpen = true;
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (layer == null || (shown && attached(layer)))
		{
			return;
		}
		Deal deal = tracker.deal();
		if (deal == null)
		{
			return;
		}
		long amount = amount(deal);
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (amount <= 0 || input == null)
		{
			return;
		}
		// Below both the "Enter amount:" title and the input line, from where the client actually laid
		// them out (their original Y is relative to a centred position mode, so it cannot be added to).
		int below = input.getRelativeY() + input.getHeight();
		if (title != null)
		{
			below = Math.max(below, title.getRelativeY() + title.getHeight());
		}
		int y = Math.min(below + LINE_GAP, Math.max(0, layer.getHeight() - LINE_HEIGHT));
		// One widget for the life of the layer: reuse ours when the client still has it, so prompts do
		// not pile up hidden children, and never delete children we did not create.
		Widget target = attached(layer) ? line : null;
		if (target == null)
		{
			target = layer.createChild(-1, WidgetType.TEXT);
			target.setTextColor(LINE_COLOR);
			target.setFontId(FontID.PLAIN_12);
			target.setTextShadowed(true);
			target.setXTextAlignment(WidgetTextAlignment.CENTER);
			target.setOriginalX(0);
			target.setOriginalHeight(LINE_HEIGHT);
			target.setAction(0, "Fill");
			target.setHasListener(true);
			target.setOnOpListener((JavaScriptCallback) event -> fill());
			line = target;
		}
		target.setText("Trade Finder: " + CoinAmounts.format(amount) + " (click to fill)");
		target.setOriginalY(y);
		target.setOriginalWidth(layer.getWidth());
		target.setHidden(false);
		target.revalidate();
		shown = true;
	}

	/** Whether the client still holds our line as a child of the prompt layer (the game may rebuild the layer). */
	private boolean attached(Widget layer)
	{
		if (line == null)
		{
			return false;
		}
		Widget[] children = layer.getDynamicChildren();
		if (children == null)
		{
			return false;
		}
		for (Widget child : children)
		{
			if (child == line)
			{
				return true;
			}
		}
		return false;
	}

	/** The click: put the amount still missing into the prompt's input. The player presses Enter. */
	private void fill()
	{
		Deal deal = tracker.deal();
		if (deal == null || !tracker.canOffer() || !promptOpen())
		{
			return;
		}
		long amount = amount(deal);
		if (amount <= 0)
		{
			return;
		}
		// The amount prompt's own input (MESLAYERINPUT), never the chat line (CHATINPUT): writing to the
		// chat input would be autotyping, which the Plugin Hub forbids. The prompt shows its input with
		// a trailing "*" cursor; set the text so the player sees what Enter will submit.
		String text = String.valueOf(amount);
		client.setVarcStrValue(VarClientID.MESLAYERINPUT, text);
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (input != null)
		{
			input.setText(text + "*");
		}
	}

	/** What the prompt asks for now, from the current offer; 0 when nothing is missing. */
	private long amount(Deal deal)
	{
		Map<Integer, Long> mine = tracker.myOffer();
		TradeNeeds.Target target;
		if (offerItemId == NOT_AN_OFFER)
		{
			return 0;
		}
		else if (offerItemId == UNKNOWN_ITEM)
		{
			target = TradeNeeds.owedTarget(deal);
		}
		else
		{
			target = TradeNeeds.targetOf(deal, offerItemId);
		}
		return TradeNeeds.promptAmount(deal, mine, target);
	}

	private boolean promptOpen()
	{
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != MESLAYERMODE_NUMBER)
		{
			return false;
		}
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		return title != null && !title.isHidden() && title.getText() != null
			&& Text.removeTags(title.getText()).toLowerCase().contains(PROMPT_TITLE);
	}

	/**
	 * Take the line off screen when the prompt closes. It is hidden, not deleted: `Widget` can only
	 * delete all of a layer's children, which would also remove what the game or another plugin put
	 * there. The same widget is shown again for the next prompt.
	 */
	void hide()
	{
		if (shown && line != null)
		{
			line.setHidden(true);
		}
		shown = false;
	}

	/** Plugin shutdown. */
	void clear()
	{
		hide();
		line = null;
		offerItemId = UNKNOWN_ITEM;
		promptWasOpen = false;
	}
}
