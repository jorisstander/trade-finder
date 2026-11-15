package com.tradefinder;

import com.tradefinder.PanelWidgets.Card;
import com.tradefinder.PanelWidgets.FilledButton;
import com.tradefinder.PanelWidgets.Pill;
import com.tradefinder.PanelWidgets.StatusDot;
import com.tradefinder.PanelWidgets.TextLink;
import com.tradefinder.PanelWidgets.ThinBar;
import com.tradefinder.api.Deal;
import com.tradefinder.api.DealHistory;
import com.tradefinder.api.Me;
import com.tradefinder.api.Order;
import com.tradefinder.api.OrderRequest;
import com.tradefinder.api.PriceInfo;
import com.tradefinder.api.TradeFinderApi;
import com.tradefinder.api.Reputation;
import com.tradefinder.api.Side;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

/**
 * Side panel: a header (title, connection dot, History), four tabs (Deal, Orders n/5, Buy, Sell)
 * and a history view that replaces the tabs while open. Everything here runs on the Swing thread;
 * the plugin marshals backend results onto it.
 */
class TradeFinderPanel extends PluginPanel
{
	private static final int MAX_SEARCH_RESULTS = 8;
	private static final int MIN_SEARCH_CHARS = 2;
	private static final int SEARCH_DEBOUNCE_MS = 250;
	private static final int ICON_SIZE = 32;
	private static final int ROW_ICON_SIZE = 24;
	private static final int PRICE_CHECK_DELAY_MS = 400;
	/** Width in px that wrapped (html) text may use inside a card. */
	private static final int WRAP_WIDTH = 185;
	/** Space between cards. */
	private static final int GAP = 6;
	/** Text widths beside a 32 px icon, a 24 px icon, and a 24 px icon plus the X button (the card is about 189 px wide). */
	private static final int ITEM_TEXT_WIDTH = 145;
	private static final int ROW_TEXT_WIDTH = 150;
	private static final int ORDER_TEXT_WIDTH = 125;
	/** About what a tab can show: a quarter of the panel minus the gaps and the tab's own padding. */
	private static final int TAB_TEXT_WIDTH = 49;
	private static final float CLOCK_FONT_SIZE = 24f;
	/** The server repeats partner_nearby on each own heartbeat (30 s); this long without one means "not seen". */
	private static final int PARTNER_AWAY_MS = 75_000;
	private static final Color WARN_RED = new Color(220, 80, 80);
	private static final Color OK_GREEN = new Color(100, 200, 100);
	private static final Color WARN_YELLOW = new Color(230, 200, 80);
	private static final Color SELL_COLOR = ColorScheme.BRAND_ORANGE;
	private static final Color BUY_COLOR = OK_GREEN;
	private static final String ORANGE_HEX = "#ff981f";
	private static final String RED_HEX = "#dc5050";
	private static final String GREEN_HEX = "#64c864";

	private final TradeFinderPlugin plugin;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executor;

	// header
	private final StatusDot statusDot = new StatusDot();
	private final TextLink historyButton = new TextLink("History", ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel noticeLabel = new JLabel();

	// tabs
	private final JPanel body = new JPanel(new BorderLayout());
	private final JPanel tabsView = new JPanel(new BorderLayout());
	private final JPanel display = new JPanel(new BorderLayout());
	private final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
	private final MaterialTab dealTab;
	private final MaterialTab ordersTab;
	private final MaterialTab buyTab;
	private final MaterialTab sellTab;

	// deal tab: no deal
	private final JPanel dealEmptyBox = new JPanel();
	private final JLabel lastDealCaption = new JLabel("Last deal");
	private final JPanel lastDealHolder = new JPanel(new BorderLayout());
	// deal tab: a deal
	private final JPanel dealBody = new JPanel();
	private final Card itemCard = new Card();
	private final JLabel dealIcon = new JLabel();
	private final JLabel dealHeadline = new JLabel();
	private final JLabel dealTotal = new JLabel();
	private final JLabel dealPartner = new JLabel();
	private final Pill partnerPill = new Pill();
	private final JLabel dealReputation = new JLabel();
	private final JLabel dealMeet = new JLabel();
	private final JLabel dealClock = new JLabel(" ");
	private final ThinBar clockBar = new ThinBar();
	private final JButton hopButton = new JButton("Hop");
	private final JButton worldFullButton = new JButton("World full");
	private final TextLink dealCancel = new TextLink("Cancel deal", ColorScheme.LIGHT_GRAY_COLOR);
	// "Cancel deal" asks first: this box replaces the link until the player confirms or keeps the deal.
	private final JPanel cancelConfirm = new JPanel();
	private final JLabel cancelWarning = new JLabel(" ");
	private final TextLink cancelYes = new TextLink(CancelWarning.BUTTON_YES, WARN_RED);
	private final TextLink cancelNo = new TextLink(CancelWarning.BUTTON_NO, ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel dealStatus = new JLabel(" ");
	private boolean cancelConfirming;
	private boolean nearGe;
	/** The status line shows how the last deal ended; it stays until the next deal or a logout. */
	private boolean endingShown;
	/** The world the client is on (0 while logged out); drives the Hop button. */
	private int currentWorld;
	/** The meeting world World full was pressed for; the button stays off until the card shows another world. */
	private int worldFullPressedWorld;

	// orders tab
	private final JPanel ordersList = new JPanel();
	private final JLabel ordersMessage = new JLabel(" ");
	private final JLabel slotsCaption = new JLabel(" ");
	// Slots and posting pause, from GET /me (slots also from the order list itself).
	private int maxSlots = 5;
	private int slotsUsed;
	private List<Order> lastOrders = List.of();
	@Nullable
	private Instant blockedUntil;
	private String blockedReason = "";

	// forms
	private final OrderForm buyForm;
	private final OrderForm sellForm;
	@Nullable
	private OrderForm postingForm;

	// history view
	private final JPanel historyView = new JPanel(new BorderLayout(0, GAP));
	private final JLabel historyCounters = new JLabel();
	private final JPanel historyList = new JPanel();
	private final JLabel historyEmpty = new JLabel("No deals yet");
	private boolean historyOpen;

	private final Timer countdownTimer;
	private final Timer partnerAwayTimer;
	private final Timer blockTimer;

	private boolean backendEnabled;
	private boolean registered;
	/** Membership of the logged-in account right now, from the client. */
	private boolean member;
	private boolean live;
	/**
	 * The server cannot be reached: registration gets no answer, or the socket keeps failing to
	 * reconnect. A single failed REST call is not this; it shows its own line and nothing is hidden.
	 */
	private boolean registrationDown;
	/** The server closed the socket because this account is blocked. */
	private boolean blocked;
	private boolean socketDown;
	private String registeredRsn;
	private String dealItemName = "";
	@Nullable
	private Deal deal;
	@Nullable
	private Instant dealExpiresAt;
	private Duration dealTotalTime = MeetingClock.FALLBACK_TOTAL;

	TradeFinderPanel(TradeFinderPlugin plugin, ItemManager itemManager, ScheduledExecutorService executor,
		boolean backendEnabled)
	{
		// Default wrapper: RuneLite puts the panel in a scroll pane, so long lists scroll.
		super();
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.executor = executor;
		this.backendEnabled = backendEnabled;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Timers first: building the sections below already calls showDeal(null), which stops them.
		countdownTimer = new Timer(1000, e -> updateCountdown());
		partnerAwayTimer = new Timer(PARTNER_AWAY_MS, e -> showPartnerNearby(false));
		partnerAwayTimer.setRepeats(false);
		blockTimer = new Timer(1000, e -> refreshBlock());

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(buildHeader());
		noticeLabel.setFont(FontManager.getRunescapeSmallFont());
		noticeLabel.setBorder(new EmptyBorder(0, 2, GAP, 2));
		noticeLabel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(noticeLabel);

		buyForm = new OrderForm(Side.BUY);
		sellForm = new OrderForm(Side.SELL);
		dealTab = tab("Deal", buildDealTab());
		ordersTab = tab("Orders", buildOrdersTab());
		buyTab = tab("Buy", buyForm.panel);
		sellTab = tab("Sell", sellForm.panel);
		tabGroup.setLayout(new GridLayout(1, 0, 2, 0));
		tabGroup.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabGroup.setBorder(new EmptyBorder(0, 0, GAP, 0));
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabsView.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabsView.add(tabGroup, BorderLayout.NORTH);
		tabsView.add(display, BorderLayout.CENTER);
		buildHistoryView();

		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setAlignmentX(LEFT_ALIGNMENT);
		body.add(tabsView, BorderLayout.CENTER);
		content.add(body);
		add(content, BorderLayout.NORTH);

		tabGroup.select(dealTab);
		showDeal(null);
		refreshSlots();
		refreshStatus();
	}

	// ---- building ----

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(GAP, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(2, 2, GAP, 2));
		header.setAlignmentX(LEFT_ALIGNMENT);

		JLabel title = new JLabel("Trade Finder");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.CENTER);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setBackground(ColorScheme.DARK_GRAY_COLOR);
		right.add(statusDot);
		right.add(Box.createHorizontalStrut(10));
		historyButton.setToolTipText("Deal history");
		historyButton.onClick(() -> setHistoryOpen(!historyOpen));
		right.add(historyButton);
		header.add(right, BorderLayout.EAST);
		return header;
	}

	private MaterialTab tab(String title, JComponent content)
	{
		MaterialTab tab = new MaterialTab(title, tabGroup, content);
		tab.setFont(FontManager.getRunescapeSmallFont());
		// Runs before the selection changes; the label refresh must see the new selection.
		tab.setOnSelectEvent(() ->
		{
			SwingUtilities.invokeLater(this::refreshDealTabLabel);
			return true;
		});
		tabGroup.addTab(tab);
		return tab;
	}

	private static JPanel tabPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(0, 0, 2, 0));
		return panel;
	}

	private static JLabel label(Color color)
	{
		JLabel label = new JLabel(" ");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel boldLabel(Color color)
	{
		JLabel label = label(color);
		label.setFont(FontManager.getRunescapeBoldFont());
		return label;
	}

	/** Text that wraps inside a card instead of being clipped. */
	private static String wrap(String html)
	{
		return "<html><div style='width:" + WRAP_WIDTH + "px'>" + html + "</div></html>";
	}

	/** Wrapped text of a fixed width, so a long line breaks instead of being cut off. */
	private static String wrapTo(int px, String html)
	{
		return "<html><div style='width:" + px + "px'>" + html + "</div></html>";
	}

	private static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Left aligned like its siblings and as wide as the column, whatever its text width. */
	private static <T extends JComponent> T stretch(T component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return component;
	}

	private static JPanel transparent(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	private static JPanel verticalBox()
	{
		JPanel panel = transparent(null);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return panel;
	}

	private JPanel buildDealTab()
	{
		JPanel panel = tabPanel();

		// No deal: centred title, a grey line, then the last deal.
		dealEmptyBox.setLayout(new BoxLayout(dealEmptyBox, BoxLayout.Y_AXIS));
		dealEmptyBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		dealEmptyBox.setAlignmentX(LEFT_ALIGNMENT);
		// Every child is left aligned and stretched to the full width, so BoxLayout does not shift any of them.
		JLabel none = boldLabel(Color.WHITE);
		none.setText("No active deal");
		none.setHorizontalAlignment(JLabel.CENTER);
		none.setBorder(new EmptyBorder(14, 0, 2, 0));
		dealEmptyBox.add(stretch(none));
		JLabel hint = label(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setText(wrapTo(WRAP_WIDTH, "<div style='text-align:center'>Post an order and we find you a partner</div>"));
		hint.setHorizontalAlignment(JLabel.CENTER);
		dealEmptyBox.add(stretch(hint));
		lastDealCaption.setFont(FontManager.getRunescapeSmallFont());
		lastDealCaption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lastDealCaption.setAlignmentX(LEFT_ALIGNMENT);
		lastDealCaption.setBorder(new EmptyBorder(16, 2, 4, 0));
		lastDealCaption.setVisible(false);
		dealEmptyBox.add(lastDealCaption);
		lastDealHolder.setOpaque(false);
		lastDealHolder.setAlignmentX(LEFT_ALIGNMENT);
		lastDealHolder.setVisible(false);
		dealEmptyBox.add(lastDealHolder);
		panel.add(dealEmptyBox);

		// A deal: item card, partner card, meeting card, quiet cancel.
		dealBody.setLayout(new BoxLayout(dealBody, BoxLayout.Y_AXIS));
		dealBody.setBackground(ColorScheme.DARK_GRAY_COLOR);
		dealBody.setAlignmentX(LEFT_ALIGNMENT);

		itemCard.setLayout(new BorderLayout(8, 0));
		itemCard.setAlignmentX(LEFT_ALIGNMENT);
		dealIcon.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		itemCard.add(dealIcon, BorderLayout.WEST);
		JPanel lines = verticalBox();
		dealHeadline.setFont(FontManager.getRunescapeBoldFont());
		dealHeadline.setForeground(Color.WHITE);
		dealHeadline.setAlignmentX(LEFT_ALIGNMENT);
		dealTotal.setAlignmentX(LEFT_ALIGNMENT);
		lines.add(dealHeadline);
		dealTotal.setFont(FontManager.getRunescapeSmallFont());
		dealTotal.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lines.add(dealTotal);
		itemCard.add(lines, BorderLayout.CENTER);
		dealBody.add(itemCard);
		dealBody.add(Box.createVerticalStrut(GAP));

		Card partnerCard = new Card();
		partnerCard.setLayout(new BorderLayout(0, 2));
		partnerCard.setAlignmentX(LEFT_ALIGNMENT);
		JPanel nameRow = transparent(new BorderLayout(GAP, 0));
		dealPartner.setFont(FontManager.getRunescapeBoldFont());
		dealPartner.setForeground(Color.WHITE);
		nameRow.add(dealPartner, BorderLayout.CENTER);
		JPanel pillBox = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		pillBox.add(partnerPill);
		nameRow.add(pillBox, BorderLayout.EAST);
		partnerCard.add(nameRow, BorderLayout.NORTH);
		dealReputation.setFont(FontManager.getRunescapeSmallFont());
		dealReputation.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		partnerCard.add(dealReputation, BorderLayout.CENTER);
		dealBody.add(partnerCard);
		dealBody.add(Box.createVerticalStrut(GAP));

		Card meetCard = new Card();
		meetCard.setLayout(new BoxLayout(meetCard, BoxLayout.Y_AXIS));
		meetCard.setAlignmentX(LEFT_ALIGNMENT);
		dealMeet.setFont(FontManager.getRunescapeSmallFont());
		dealMeet.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		dealMeet.setAlignmentX(LEFT_ALIGNMENT);
		meetCard.add(dealMeet);
		meetCard.add(Box.createVerticalStrut(4));
		dealClock.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, CLOCK_FONT_SIZE));
		dealClock.setForeground(WARN_YELLOW);
		dealClock.setAlignmentX(LEFT_ALIGNMENT);
		meetCard.add(dealClock);
		meetCard.add(Box.createVerticalStrut(4));
		clockBar.setAlignmentX(LEFT_ALIGNMENT);
		meetCard.add(clockBar);
		meetCard.add(Box.createVerticalStrut(GAP));
		JPanel buttons = transparent(new GridLayout(1, 0, 4, 0));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		for (JButton button : new JButton[]{hopButton, worldFullButton})
		{
			button.setFocusPainted(false);
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setMargin(new Insets(1, 6, 1, 6));
			buttons.add(button);
		}
		hopButton.addActionListener(e ->
		{
			if (deal != null)
			{
				plugin.hopToWorld(deal.getWorld());
			}
		});
		worldFullButton.setToolTipText("The meeting world is full: move the deal to the next backup world");
		worldFullButton.addActionListener(e ->
		{
			if (deal != null)
			{
				worldFullButton.setEnabled(false);
				worldFullPressedWorld = deal.getWorld();
				plugin.worldFull(deal.getId(), deal.getWorld());
			}
		});
		meetCard.add(buttons);
		dealBody.add(meetCard);
		dealBody.add(Box.createVerticalStrut(GAP));

		dealCancel.setToolTipText("Cancel your order: the deal is off and your partner's order stays open for other players");
		dealCancel.setBorder(new EmptyBorder(4, 8, 4, 8));
		dealCancel.onClick(() -> setCancelConfirming(true));
		cancelYes.onClick(() ->
		{
			if (deal != null)
			{
				cancelYes.setEnabled(false);
				plugin.cancelOrder(deal.getMyOrderId());
			}
		});
		cancelNo.onClick(() -> setCancelConfirming(false));
		JPanel cancelRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		cancelRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cancelRow.setAlignmentX(LEFT_ALIGNMENT);
		cancelRow.add(dealCancel);
		dealBody.add(cancelRow);
		cancelConfirm.setLayout(new BoxLayout(cancelConfirm, BoxLayout.Y_AXIS));
		cancelConfirm.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cancelConfirm.setAlignmentX(LEFT_ALIGNMENT);
		cancelWarning.setFont(FontManager.getRunescapeSmallFont());
		cancelWarning.setForeground(WARN_YELLOW);
		cancelWarning.setAlignmentX(LEFT_ALIGNMENT);
		cancelConfirm.add(cancelWarning);
		cancelConfirm.add(Box.createVerticalStrut(4));
		JPanel confirmRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		confirmRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		confirmRow.setAlignmentX(LEFT_ALIGNMENT);
		cancelYes.setBorder(new EmptyBorder(4, 4, 4, 4));
		cancelNo.setBorder(new EmptyBorder(4, 4, 4, 4));
		confirmRow.add(cancelYes);
		confirmRow.add(cancelNo);
		cancelConfirm.add(confirmRow);
		cancelConfirm.setVisible(false);
		dealBody.add(cancelConfirm);
		panel.add(dealBody);

		panel.add(Box.createVerticalStrut(GAP));
		dealStatus.setFont(FontManager.getRunescapeSmallFont());
		dealStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		dealStatus.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(dealStatus);
		return panel;
	}

	/** Swap the Cancel deal link for the question (with what cancelling costs) and back. Swing thread. */
	private void setCancelConfirming(boolean confirming)
	{
		cancelConfirming = confirming;
		cancelYes.setEnabled(true);
		dealCancel.setVisible(!confirming);
		cancelConfirm.setVisible(confirming);
		refreshCancelWarning();
		revalidate();
		repaint();
	}

	/** On the deal's world and inside the GE area, as far as the client says. */
	private boolean atMeetingSpot()
	{
		return deal != null && nearGe && currentWorld > 0 && currentWorld == deal.getWorld();
	}

	/** Whether the local player stands inside the Grand Exchange area (from the presence check). Swing thread. */
	void setNearGe(boolean nearGe)
	{
		this.nearGe = nearGe;
		refreshCancelWarning();
	}

	private void refreshCancelWarning()
	{
		if (cancelConfirming && deal != null)
		{
			cancelWarning.setText(wrap(escape(CancelWarning.text(Instant.now(), parseInstant(deal.getFreeCancelAt()), atMeetingSpot()))));
		}
	}

	private JPanel buildOrdersTab()
	{
		JPanel panel = tabPanel();
		slotsCaption.setFont(FontManager.getRunescapeSmallFont());
		slotsCaption.setAlignmentX(LEFT_ALIGNMENT);
		slotsCaption.setBorder(new EmptyBorder(0, 2, 4, 0));
		slotsCaption.setVisible(false);
		panel.add(slotsCaption);
		ordersList.setLayout(new BoxLayout(ordersList, BoxLayout.Y_AXIS));
		ordersList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		ordersList.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(ordersList);
		ordersMessage.setFont(FontManager.getRunescapeSmallFont());
		ordersMessage.setForeground(WARN_RED);
		ordersMessage.setAlignmentX(LEFT_ALIGNMENT);
		ordersMessage.setBorder(new EmptyBorder(2, 2, 0, 0));
		panel.add(ordersMessage);
		showOrders(List.of());
		return panel;
	}

	private void buildHistoryView()
	{
		historyView.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel titleBar = new JPanel(new BorderLayout(0, 2));
		titleBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titleBar.setBorder(new EmptyBorder(2, 2, 2, 2));
		TextLink back = new TextLink("< Back", ColorScheme.LIGHT_GRAY_COLOR);
		back.setToolTipText("Back to the tabs");
		back.onClick(() -> setHistoryOpen(false));
		titleBar.add(back, BorderLayout.WEST);
		historyCounters.setFont(FontManager.getRunescapeSmallFont());
		historyCounters.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		titleBar.add(historyCounters, BorderLayout.SOUTH);
		historyView.add(titleBar, BorderLayout.NORTH);

		historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
		historyList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		historyEmpty.setFont(FontManager.getRunescapeSmallFont());
		historyEmpty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		historyEmpty.setAlignmentX(LEFT_ALIGNMENT);
		historyList.add(historyEmpty);
		historyView.add(historyList, BorderLayout.CENTER);
	}

	private static FlatTextField field()
	{
		FlatTextField field = new FlatTextField();
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		field.setBorder(new EmptyBorder(4, 6, 4, 6));
		field.setPreferredSize(new Dimension(60, 26));
		return field;
	}

	private static JPanel captioned(String caption, JComponent field)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel label = new JLabel(caption);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(label, BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	/** An item icon of {@code size} px that fills in once the sprite has loaded. */
	private JLabel itemIcon(int itemId, int size)
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(size, size));
		AsyncBufferedImage image = itemManager.getImage(itemId);
		if (image != null)
		{
			image.onLoaded(() -> icon.setIcon(new ImageIcon(ImageUtil.resizeImage(image, size, size))));
		}
		return icon;
	}

	/** A line of text in the small font. */
	private static JLabel line(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static Color sideColor(Side side)
	{
		return side == Side.BUY ? BUY_COLOR : SELL_COLOR;
	}

	// ---- history view ----

	private void setHistoryOpen(boolean open)
	{
		if (historyOpen == open)
		{
			return;
		}
		historyOpen = open;
		body.removeAll();
		body.add(open ? historyView : tabsView, BorderLayout.CENTER);
		historyButton.setActive(open);
		body.revalidate();
		body.repaint();
		if (open)
		{
			historyCounters.setText("Loading...");
			plugin.loadHistory();
		}
	}

	/** {@code GET /deals/history} succeeded. Swing thread. */
	void showHistory(DealHistory history)
	{
		historyCounters.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		historyCounters.setText(wrap(plural(history.getDealsDone(), "deal") + " · " + plural(history.getNoShows(), "no-show")
			+ " · " + plural(history.getCancels(), "cancel")));
		historyList.removeAll();
		List<Deal> deals = history.getDeals();
		showLastDeal(deals == null || deals.isEmpty() ? null : deals.get(0));
		if (deals == null || deals.isEmpty())
		{
			historyList.add(historyEmpty);
		}
		else
		{
			for (Deal past : deals.subList(0, Math.min(20, deals.size())))
			{
				JPanel card = historyCard(past);
				card.setAlignmentX(LEFT_ALIGNMENT);
				historyList.add(card);
				historyList.add(Box.createVerticalStrut(GAP));
			}
		}
		historyList.revalidate();
		historyList.repaint();
	}

	/** {@code GET /deals/history} failed. Swing thread. */
	void onHistoryFailed(String message)
	{
		historyCounters.setForeground(WARN_RED);
		historyCounters.setText(wrap(escape(message)));
	}

	/** A closed deal: icon, "Sold 6 Anglerfish" (the verb only when it completed), "9,000 gp · partner" and a status pill. */
	private JPanel historyCard(Deal past)
	{
		DealOutcome outcome = DealOutcome.of(past.getStatus());
		JLabel line1 = line(wrapTo(ROW_TEXT_WIDTH, escape(outcome.headline(past.getMySide(), past.getQuantity(),
			"item " + past.getItemId()))), Color.WHITE);
		plugin.resolveItemName(past.getItemId(), name -> line1.setText(wrapTo(ROW_TEXT_WIDTH,
			escape(outcome.headline(past.getMySide(), past.getQuantity(), name)))));
		String partner = past.getPartnerRsn() == null ? "" : " · " + past.getPartnerRsn();
		JLabel line2 = line(wrapTo(ROW_TEXT_WIDTH, escape(CoinAmounts.format(past.getPrice() * past.getQuantity()) + " gp" + partner)),
			ColorScheme.LIGHT_GRAY_COLOR);
		Pill pill = new Pill();
		pill.show(outcome.pill(), outcome.isGood() ? OK_GREEN : WARN_RED);

		Card card = new Card();
		card.setLayout(new BorderLayout(8, 0));
		card.add(itemIcon(past.getItemId(), ROW_ICON_SIZE), BorderLayout.WEST);
		JPanel lines = verticalBox();
		line1.setAlignmentX(LEFT_ALIGNMENT);
		line2.setAlignmentX(LEFT_ALIGNMENT);
		lines.add(line1);
		lines.add(line2);
		JPanel pillRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
		pillRow.setAlignmentX(LEFT_ALIGNMENT);
		pillRow.setBorder(new EmptyBorder(3, 0, 0, 0));
		pillRow.add(pill);
		lines.add(pillRow);
		card.add(lines, BorderLayout.CENTER);
		return card;
	}

	private static String plural(int n, String noun)
	{
		return n + " " + noun + (n == 1 ? "" : "s");
	}

	/** A wrapped line: the side word bold and coloured (buy green, sell orange), then {@code rest} (plain text). */
	private static String sideLine(Side side, int px, String rest)
	{
		return wrapTo(px, "<b><font color='" + (side == Side.BUY ? GREEN_HEX : ORANGE_HEX) + "'>" + side.getLabel()
			+ "</font></b> " + escape(rest));
	}

	/** The newest closed deal under the empty Deal tab, or nothing when there is no history. */
	private void showLastDeal(@Nullable Deal past)
	{
		lastDealHolder.removeAll();
		boolean any = past != null;
		if (any)
		{
			lastDealHolder.add(historyCard(past), BorderLayout.CENTER);
		}
		lastDealCaption.setVisible(any);
		lastDealHolder.setVisible(any);
		lastDealHolder.revalidate();
		lastDealHolder.repaint();
	}

	// ---- order form (Buy and Sell tabs) ----

	private final class OrderForm
	{
		private final Side side;
		private final JPanel panel = tabPanel();
		private final IconTextField searchField = new IconTextField();
		private final JPanel searchHolder = new JPanel(new BorderLayout());
		private final JPanel searchResults = new JPanel();
		private final JLabel selectedIcon = new JLabel();
		private final JLabel selectedName = new JLabel("No item selected");
		private final FlatTextField quantityField = field();
		private final FlatTextField priceField = field();
		private final JLabel instaBuyValue = line("", Color.WHITE);
		private final JLabel instaSellValue = line("", Color.WHITE);
		private final JLabel suggestedValue = new JLabel();
		private final JPanel priceRows = transparent(new GridLayout(0, 1, 0, 2));
		private final JLabel pricePlaceholder = line("", ColorScheme.LIGHT_GRAY_COLOR);
		private final JLabel hintLabel = label(WARN_RED);
		private final JLabel gainLabel = label(OK_GREEN);
		private final JButton postButton;
		private final JLabel messageLabel = label(ColorScheme.LIGHT_GRAY_COLOR);
		private final Timer priceCheckTimer;
		private final Timer searchTimer;

		private int selectedItemId = -1;
		private boolean selectedMembers;
		private int searchGeneration;
		@Nullable
		private PriceInfo priceInfo;
		/** False until the player touched the quantity: the first quick button then overwrites, later ones add. */
		private boolean quantityDirty;
		/** True while the code writes the quantity field, so the document listener ignores it. */
		private boolean settingQuantity;
		/** The message line shows the slots / pause / members text (ours to clear), not a posting error. */
		private boolean availabilityShown;
		/** The client thread has answered whether the selected item is a members item. */
		private boolean membersKnown;
		/** Sell form only: fill the quantity with what the inventory holds. */
		@Nullable
		private JButton allButton;

		OrderForm(Side side)
		{
			this.side = side;
			postButton = new FilledButton(side == Side.BUY ? "Post buy order" : "Post sell order", sideColor(side));
			priceCheckTimer = new Timer(PRICE_CHECK_DELAY_MS, e -> requestPriceCheck());
			priceCheckTimer.setRepeats(false);
			searchTimer = new Timer(SEARCH_DEBOUNCE_MS, e -> searchItems());
			searchTimer.setRepeats(false);

			searchField.setIcon(IconTextField.Icon.SEARCH);
			searchField.setPreferredSize(new Dimension(PANEL_WIDTH - 40, 30));
			searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
			// Enter searches at once; typing searches after a short pause, from two letters.
			searchField.addActionListener(e ->
			{
				searchTimer.stop();
				searchItems();
			});
			searchField.addClearListener(this::clearSearchResults);
			searchField.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					searchTyped();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					searchTyped();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					searchTyped();
				}
			});
			searchField.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(searchField);
			panel.add(Box.createVerticalStrut(GAP));

			// Results: one card, rows highlight on hover.
			Card resultsCard = new Card();
			resultsCard.setBorder(new EmptyBorder(4, 4, 4, 4));
			resultsCard.setLayout(new BorderLayout());
			searchResults.setLayout(new BoxLayout(searchResults, BoxLayout.Y_AXIS));
			searchResults.setOpaque(false);
			resultsCard.add(searchResults, BorderLayout.CENTER);
			searchHolder.setOpaque(false);
			searchHolder.setBorder(new EmptyBorder(0, 0, GAP, 0));
			searchHolder.add(resultsCard, BorderLayout.CENTER);
			searchHolder.setVisible(false);
			searchHolder.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(searchHolder);

			// The selected item: no icon box until there is an icon.
			JPanel selected = new JPanel(new BorderLayout(8, 0));
			selected.setBackground(ColorScheme.DARK_GRAY_COLOR);
			selected.setBorder(new EmptyBorder(0, 2, 0, 0));
			selected.setAlignmentX(LEFT_ALIGNMENT);
			selectedIcon.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
			selectedIcon.setVisible(false);
			selected.add(selectedIcon, BorderLayout.WEST);
			selectedName.setFont(FontManager.getRunescapeSmallFont());
			selectedName.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			selected.add(selectedName, BorderLayout.CENTER);
			panel.add(selected);
			panel.add(Box.createVerticalStrut(GAP));

			JPanel amounts = new JPanel(new BorderLayout(GAP, 0));
			amounts.setBackground(ColorScheme.DARK_GRAY_COLOR);
			amounts.setAlignmentX(LEFT_ALIGNMENT);
			setQuantity(1);
			quantityField.setPreferredSize(new Dimension(64, 26));
			quantityField.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					typed();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					typed();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					typed();
				}

				private void typed()
				{
					if (!settingQuantity)
					{
						quantityDirty = true;
					}
					refreshGain();
				}
			});
			amounts.add(captioned("Qty", quantityField), BorderLayout.WEST);
			priceField.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					schedulePriceCheck();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					schedulePriceCheck();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					schedulePriceCheck();
				}
			});
			amounts.add(captioned("Price each", priceField), BorderLayout.CENTER);
			panel.add(amounts);
			panel.add(Box.createVerticalStrut(4));

			JPanel quick = new JPanel(new GridLayout(1, 0, 4, 0));
			quick.setBackground(ColorScheme.DARK_GRAY_COLOR);
			quick.setAlignmentX(LEFT_ALIGNMENT);
			for (long step : new long[]{1, 10, 100, 1000})
			{
				JButton add = new JButton(String.valueOf(step));
				add.setFont(FontManager.getRunescapeSmallFont());
				add.setMargin(new Insets(1, 2, 1, 2));
				add.setFocusPainted(false);
				add.setToolTipText("First press sets the quantity to " + step + ", later presses add " + step);
				add.addActionListener(e -> addQuantity(step));
				quick.add(add);
			}
			if (side == Side.SELL)
			{
				JButton all = new JButton("All");
				all.setFont(FontManager.getRunescapeSmallFont());
				all.setMargin(new Insets(1, 2, 1, 2));
				all.setFocusPainted(false);
				all.setToolTipText("Sell every one of this item in your inventory");
				all.addActionListener(e -> fillFromInventory());
				all.setEnabled(false);
				quick.add(all);
				allButton = all;
			}
			panel.add(quick);
			panel.add(Box.createVerticalStrut(GAP));

			// Prices: one card, label left and value right, Suggested in bold.
			Card priceCard = new Card();
			priceCard.setLayout(new BorderLayout());
			priceCard.setAlignmentX(LEFT_ALIGNMENT);
			suggestedValue.setFont(FontManager.getRunescapeBoldFont());
			suggestedValue.setForeground(Color.WHITE);
			priceRows.add(priceRow("Insta buy", instaBuyValue));
			priceRows.add(priceRow("Insta sell", instaSellValue));
			JLabel suggestedLabel = new JLabel("Suggested");
			suggestedLabel.setFont(FontManager.getRunescapeBoldFont());
			suggestedLabel.setForeground(Color.WHITE);
			JPanel suggestedRow = transparent(new BorderLayout());
			suggestedRow.add(suggestedLabel, BorderLayout.WEST);
			suggestedRow.add(suggestedValue, BorderLayout.EAST);
			priceRows.add(suggestedRow);
			priceCard.add(priceRows, BorderLayout.CENTER);
			priceCard.add(pricePlaceholder, BorderLayout.NORTH);
			panel.add(priceCard);
			panel.add(Box.createVerticalStrut(GAP));
			showPriceBlock(null, "Pick an item to see the GE prices");

			hintLabel.setVisible(false);
			gainLabel.setVisible(false);
			panel.add(hintLabel);
			panel.add(gainLabel);

			postButton.setAlignmentX(LEFT_ALIGNMENT);
			postButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			postButton.addActionListener(e -> postOrder());
			panel.add(Box.createVerticalStrut(GAP));
			panel.add(postButton);
			messageLabel.setBorder(new EmptyBorder(GAP, 0, 0, 0));
			panel.add(messageLabel);
		}

		private JPanel priceRow(String caption, JLabel value)
		{
			JPanel row = transparent(new BorderLayout());
			row.add(line(caption, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
			row.add(value, BorderLayout.EAST);
			return row;
		}

		private void searchTyped()
		{
			String text = searchField.getText().trim();
			if (text.length() >= MIN_SEARCH_CHARS)
			{
				searchTimer.restart();
			}
			else
			{
				searchTimer.stop();
				clearSearchResults();
			}
		}

		private void searchItems()
		{
			String query = searchField.getText().trim();
			if (query.isEmpty())
			{
				clearSearchResults();
				return;
			}
			int generation = ++searchGeneration;
			// The item name index lives in memory, but keep the Swing thread free anyway.
			executor.execute(() ->
			{
				List<ItemPrice> matches = itemManager.search(query);
				SwingUtilities.invokeLater(() -> showSearchResults(generation, matches));
			});
		}

		private void showSearchResults(int generation, List<ItemPrice> matches)
		{
			if (generation != searchGeneration)
			{
				return;
			}
			searchResults.removeAll();
			if (matches.isEmpty())
			{
				JLabel none = line("No tradeable item matches", ColorScheme.LIGHT_GRAY_COLOR);
				none.setBorder(new EmptyBorder(2, 4, 2, 4));
				searchResults.add(none);
			}
			for (ItemPrice match : matches.subList(0, Math.min(MAX_SEARCH_RESULTS, matches.size())))
			{
				searchResults.add(resultRow(match));
			}
			searchHolder.setVisible(true);
			panel.revalidate();
			panel.repaint();
		}

		private JPanel resultRow(ItemPrice item)
		{
			boolean[] hover = {false};
			JPanel row = new JPanel(new BorderLayout(6, 0))
			{
				@Override
				protected void paintComponent(Graphics graphics)
				{
					if (hover[0])
					{
						Graphics2D g = (Graphics2D) graphics.create();
						g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g.setColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
						g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
						g.dispose();
					}
				}
			};
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(2, 4, 2, 4));
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.add(itemIcon(item.getId(), ROW_ICON_SIZE), BorderLayout.WEST);
			row.add(line(wrapTo(ROW_TEXT_WIDTH, escape(item.getName())), Color.WHITE), BorderLayout.CENTER);
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					selectItem(item.getId(), item.getName());
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover[0] = true;
					row.repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover[0] = false;
					row.repaint();
				}
			});
			return row;
		}

		private void clearSearchResults()
		{
			searchGeneration++;
			searchResults.removeAll();
			searchHolder.setVisible(false);
			panel.revalidate();
			panel.repaint();
		}

		/** Quick button: the first press sets the quantity, later presses add (an unparsable field counts as 0). */
		private void addQuantity(long step)
		{
			long current = 0;
			if (quantityDirty)
			{
				try
				{
					current = CoinAmounts.parse(quantityField.getText());
				}
				catch (NumberFormatException e)
				{
					current = 0;
				}
			}
			setQuantity(Math.min(CoinAmounts.MAX_STACK, current + step));
			quantityDirty = true;
		}

		/** Write the quantity without counting as typing. */
		private void setQuantity(long quantity)
		{
			settingQuantity = true;
			try
			{
				quantityField.setText(String.valueOf(quantity));
			}
			finally
			{
				settingQuantity = false;
			}
		}

		/** "All": ask the client thread how many of the selected item the inventory holds. */
		private void fillFromInventory()
		{
			if (selectedItemId <= 0)
			{
				return;
			}
			int itemId = selectedItemId;
			plugin.countInInventory(itemId, count ->
			{
				if (itemId != selectedItemId)
				{
					return;
				}
				setQuantity(count);
				quantityDirty = true;
				setMessage(count == 0 ? "You have none of this item in your inventory" : null);
			});
		}

		/** The All button needs a selected item and a logged-in client (currentWorld is 0 while logged out). */
		void refreshAllButton()
		{
			if (allButton != null)
			{
				allButton.setEnabled(selectedItemId > 0 && currentWorld > 0);
			}
		}

		/** Back to the empty form (after a successful post). */
		void reset()
		{
			selectedItemId = -1;
			selectedMembers = false;
			membersKnown = false;
			priceInfo = null;
			searchTimer.stop();
			searchField.setText("");
			clearSearchResults();
			selectedIcon.setIcon(null);
			selectedIcon.setVisible(false);
			selectedName.setText("No item selected");
			selectedName.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			setQuantity(1);
			quantityDirty = false;
			priceField.setText("");
			showPriceBlock(null, "Pick an item to see the GE prices");
			setMessage(null);
			refreshAllButton();
			refreshAvailability();
		}

		private void selectItem(int itemId, String name)
		{
			selectedItemId = itemId;
			selectedMembers = false;
			membersKnown = false;
			priceInfo = null;
			selectedName.setText(wrapTo(ITEM_TEXT_WIDTH, escape(name)));
			selectedName.setForeground(Color.WHITE);
			selectedIcon.setIcon(null);
			selectedIcon.setVisible(true);
			AsyncBufferedImage image = itemManager.getImage(itemId);
			if (image != null)
			{
				image.addTo(selectedIcon);
			}
			searchTimer.stop();
			clearSearchResults();
			priceField.setText("");
			showPriceBlock(null, "No GE price yet");
			setMessage(null);
			quantityDirty = false;
			refreshAllButton();
			schedulePriceCheck();
			plugin.resolveMembers(itemId, members ->
			{
				if (itemId == selectedItemId)
				{
					selectedMembers = members;
					membersKnown = true;
					refreshAvailability();
				}
			});
		}

		private void schedulePriceCheck()
		{
			priceCheckTimer.restart();
		}

		private void requestPriceCheck()
		{
			if (selectedItemId <= 0 || !registered)
			{
				return;
			}
			if (priceInfo != null)
			{
				// The prices are known; only the typed price changed.
				evaluateHint();
				return;
			}
			plugin.checkPrice(selectedItemId, null);
		}

		/** The deal zone for this form's item arrived. */
		void showPrice(int itemId, PriceInfo info)
		{
			if (itemId != selectedItemId || info.getDealZone() == null)
			{
				return;
			}
			priceInfo = info;
			showPriceBlock(info, null);
			if (priceField.getText().trim().isEmpty())
			{
				// Prefill the suggested price; the document listener re-evaluates the hint.
				priceField.setText(String.valueOf(info.getDealZone().getSuggested()));
				return;
			}
			evaluateHint();
		}

		void showPriceError(int itemId, String message)
		{
			if (itemId != selectedItemId)
			{
				return;
			}
			priceInfo = null;
			showPriceBlock(null, "No GE price yet");
		}

		/** The three-row price card when known, else one grey line saying why not. */
		private void showPriceBlock(@Nullable PriceInfo info, @Nullable String placeholder)
		{
			boolean known = info != null && info.getDealZone() != null;
			if (known)
			{
				instaBuyValue.setText(CoinAmounts.format(info.getInstaBuy()));
				instaSellValue.setText(CoinAmounts.format(info.getInstaSell()));
				suggestedValue.setText(CoinAmounts.format(info.getDealZone().getSuggested()));
			}
			priceRows.setVisible(known);
			pricePlaceholder.setText(known ? " " : placeholder);
			pricePlaceholder.setVisible(!known);
			setHint(null);
			refreshGain();
		}

		/** Compare the typed price with the zone: nothing inside it, one red line outside. */
		private void evaluateHint()
		{
			refreshGain();
			if (priceInfo == null || priceInfo.getDealZone() == null)
			{
				return;
			}
			PriceInfo.DealZone zone = priceInfo.getDealZone();
			String text = priceField.getText().trim();
			if (text.isEmpty())
			{
				setHint(null);
				return;
			}
			long price;
			try
			{
				price = CoinAmounts.parse(text);
			}
			catch (NumberFormatException e)
			{
				setHint("Price is not a number");
				return;
			}
			boolean buying = side == Side.BUY;
			if (price < zone.getLow())
			{
				setHint(buying ? "Below GE range: sellers do better at the GE" : "Below GE range: you'd get less than at the GE");
			}
			else if (price > zone.getHigh())
			{
				setHint(buying ? "Above GE range: you'd pay more than at the GE" : "Above GE range: buyers do better at the GE");
			}
			else
			{
				setHint(null);
			}
		}

		/** The green line: what this order saves compared with the GE, shown only when positive. */
		private void refreshGain()
		{
			long gain = 0;
			if (priceInfo != null && priceInfo.getDealZone() != null)
			{
				try
				{
					long price = CoinAmounts.parse(priceField.getText());
					long quantity = Math.min(CoinAmounts.parse(quantityField.getText()), CoinAmounts.MAX_STACK);
					gain = side == Side.SELL
						? GeSavings.sellGain(price, quantity, priceInfo.getDealZone().getLow())
						: GeSavings.buyGain(priceInfo.getInstaBuy(), price, quantity);
				}
				catch (NumberFormatException e)
				{
					gain = 0;
				}
			}
			if (gain > 0)
			{
				String amount = CoinAmounts.format(gain) + " gp";
				gainLabel.setText(wrap(side == Side.SELL ? "You keep " + amount + " more than on the GE"
					: "You pay " + amount + " less than on the GE"));
			}
			gainLabel.setVisible(gain > 0);
		}

		private void setHint(@Nullable String text)
		{
			hintLabel.setText(text == null ? " " : wrap(text));
			hintLabel.setVisible(text != null);
		}

		private void postOrder()
		{
			if (!registered)
			{
				setMessage("You are not connected to the Trade Finder server yet.");
				return;
			}
			if (selectedItemId <= 0)
			{
				setMessage("Pick an item first");
				return;
			}
			String membersBlock = OrderRules.membersBlock(selectedMembers, member);
			if (membersBlock != null)
			{
				setMessage(membersBlock);
				return;
			}
			long quantity;
			long price;
			try
			{
				quantity = CoinAmounts.parse(quantityField.getText());
			}
			catch (NumberFormatException e)
			{
				setMessage("Quantity must be a positive whole number");
				return;
			}
			if (quantity > CoinAmounts.MAX_STACK)
			{
				setMessage("Quantity cannot exceed " + CoinAmounts.format(CoinAmounts.MAX_STACK));
				return;
			}
			try
			{
				price = CoinAmounts.parse(priceField.getText());
			}
			catch (NumberFormatException e)
			{
				setMessage("Price must be a positive number of coins, e.g. 1.5m");
				return;
			}
			postButton.setEnabled(false);
			messageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			messageLabel.setText("Posting...");
			postingForm = this;
			plugin.postOrder(new OrderRequest(selectedItemId, side, quantity, price, selectedMembers));
		}

		/** Red error line under the button, or blank. */
		private void setMessage(@Nullable String text)
		{
			availabilityShown = false;
			messageLabel.setForeground(WARN_RED);
			messageLabel.setText(text == null ? " " : wrap(escape(text))); // may carry the server's own words
		}

		void setRegistered(boolean registered)
		{
			refreshAvailability();
			if (registered)
			{
				schedulePriceCheck();
			}
		}

		private void showAvailability(String text)
		{
			postButton.setEnabled(false);
			messageLabel.setForeground(WARN_YELLOW);
			messageLabel.setText(wrap(escape(text))); // includes the server's pause reason
			availabilityShown = true;
		}

		/** Enable Post, or say why not: a members item without membership, posting paused (with a countdown), or every slot in use. */
		void refreshAvailability()
		{
			String membersBlock = OrderRules.membersBlock(selectedMembers, member);
			if (membersBlock != null)
			{
				showAvailability(membersBlock);
			}
			else if (blockedUntil != null)
			{
				showAvailability("Posting paused: " + blockedReason + " · "
					+ clock(Duration.between(Instant.now(), blockedUntil)));
			}
			else if (slotsUsed >= maxSlots)
			{
				showAvailability("All " + maxSlots + " slots in use. Cancel an order first.");
			}
			else if (registered && !live)
			{
				// The server learns membership from the first heartbeat, which the socket sends once it is open.
				showAvailability("Connecting to the server...");
			}
			else
			{
				postButton.setEnabled(registered && live && (selectedItemId <= 0 || membersKnown) && postingForm != this);
				if (availabilityShown)
				{
					availabilityShown = false;
					messageLabel.setText(" ");
				}
			}
		}

		/** The reply to the Post press will never come (logout, server turned off): leave "Posting...". */
		void postingLost()
		{
			setMessage(null);
			refreshAvailability();
		}

		void dispose()
		{
			priceCheckTimer.stop();
			searchTimer.stop();
		}
	}

	// ---- price zone (routed to both forms; each keeps only its own item) ----

	/** Deal zone for an item, from {@code GET /prices}. Swing thread. */
	void showPrice(int itemId, PriceInfo info)
	{
		buyForm.showPrice(itemId, info);
		sellForm.showPrice(itemId, info);
	}

	/** {@code GET /prices} failed (404 until the backend has polled the Wiki). Swing thread. */
	void showPriceError(int itemId, String message)
	{
		buyForm.showPriceError(itemId, message);
		sellForm.showPriceError(itemId, message);
	}

	// ---- posting ----

	/** The answer to a pending Post will not arrive (the session is gone): free the form. Swing thread. */
	private void forgetPosting()
	{
		OrderForm form = postingForm;
		postingForm = null;
		if (form != null)
		{
			form.postingLost();
		}
	}

	/** {@code POST /orders} succeeded. Swing thread. */
	void onOrderPosted(Order order, @Nullable Deal deal)
	{
		OrderForm form = postingForm;
		postingForm = null;
		if (form != null)
		{
			form.refreshAvailability();
		}
		buyForm.reset();
		sellForm.reset();
		if (deal != null)
		{
			showDeal(deal);
		}
		else
		{
			setHistoryOpen(false);
			tabGroup.select(ordersTab);
		}
	}

	/** {@code POST /orders} failed. Swing thread. */
	void onOrderFailed(String message)
	{
		OrderForm form = postingForm;
		postingForm = null;
		if (form != null)
		{
			form.refreshAvailability();
			form.setMessage(message);
		}
	}

	// ---- own orders ----

	/**
	 * Replace the order list: one card per open or matched order, then the free slots, so the
	 * five slots are always visible. Item names are resolved on the client thread later. Swing thread.
	 */
	void showOrders(List<Order> orders)
	{
		lastOrders = orders;
		ordersList.removeAll();
		int used = 0;
		for (Order order : orders)
		{
			if (!"open".equals(order.getStatus()) && !"matched".equals(order.getStatus()))
			{
				continue;
			}
			used++;
			ordersList.add(orderCard(order));
			ordersList.add(Box.createVerticalStrut(GAP));
		}
		for (int i = used; i < maxSlots; i++)
		{
			ordersList.add(emptySlotCard(i == used));
			ordersList.add(Box.createVerticalStrut(GAP));
		}
		slotsUsed = used;
		refreshSlots();
		ordersList.revalidate();
		ordersList.repaint();
	}

	/** A free slot: dashed outline, "Empty slot"; the first one also offers "+ Buy" and "+ Sell". */
	private JPanel emptySlotCard(boolean first)
	{
		Card card = new Card().dashed();
		card.setLayout(new BorderLayout(GAP, 0));
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.add(line("Empty slot", ColorScheme.MEDIUM_GRAY_COLOR), BorderLayout.WEST);
		if (first)
		{
			JPanel links = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
			links.add(new TextLink("+ Buy", BUY_COLOR).onClick(() -> tabGroup.select(buyTab)));
			links.add(new TextLink("+ Sell", SELL_COLOR).onClick(() -> tabGroup.select(sellTab)));
			card.add(links, BorderLayout.EAST);
		}
		return card;
	}

	/** {@code GET /me}: slots used and any posting pause. Swing thread. */
	void showMe(Me me)
	{
		if (me.getMaxOpenOrders() > 0 && me.getMaxOpenOrders() != maxSlots)
		{
			maxSlots = me.getMaxOpenOrders();
			showOrders(lastOrders);
		}
		slotsUsed = me.getSlotsUsed();
		blockedUntil = parseInstant(me.getBlockedUntil());
		blockedReason = me.getBlockedReason() == null ? "" : me.getBlockedReason();
		refreshSlots();
		refreshBlock();
	}

	/** Logged out or server off: no slots, no pause. Swing thread. */
	private void clearMe()
	{
		blockedUntil = null;
		blockedReason = "";
		blockTimer.stop();
		refreshSlots();
	}

	/**
	 * The slot count lives in the Orders tab title ("Orders 2/5") when that fits the tab; otherwise
	 * the tab says "Orders" and a caption inside it says "2 / 5 slots used".
	 */
	private void refreshSlots()
	{
		if (ordersTab != null)
		{
			boolean fits = ordersTab.getFontMetrics(ordersTab.getFont()).stringWidth("Orders " + maxSlots + "/" + maxSlots)
				<= TAB_TEXT_WIDTH;
			ordersTab.setText(fits ? "Orders " + slotsUsed + "/" + maxSlots : "Orders");
			slotsCaption.setText(slotsUsed + " / " + maxSlots + " slots used");
			slotsCaption.setForeground(slotsUsed >= maxSlots ? WARN_YELLOW : ColorScheme.LIGHT_GRAY_COLOR);
			slotsCaption.setVisible(!fits);
		}
		buyForm.refreshAvailability();
		sellForm.refreshAvailability();
	}

	/** Each second while posting is paused: tick the countdown, lift the pause when it ends. */
	private void refreshBlock()
	{
		if (blockedUntil != null && !Instant.now().isBefore(blockedUntil))
		{
			blockedUntil = null;
			blockedReason = "";
		}
		if (blockedUntil == null)
		{
			blockTimer.stop();
		}
		else if (!blockTimer.isRunning())
		{
			blockTimer.start();
		}
		buyForm.refreshAvailability();
		sellForm.refreshAvailability();
	}

	/** m:ss, or h:mm:ss for the hour-long block. */
	private static String clock(Duration left)
	{
		long seconds = Math.max(0, left.getSeconds());
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long rest = seconds % 60;
		return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest) : String.format("%d:%02d", minutes, rest);
	}

	/** One order: icon, "Sell Anglerfish", "1,500 each · waiting", X, a thin progress bar and "3 of 6 sold". */
	private JPanel orderCard(Order order)
	{
		boolean matched = "matched".equals(order.getStatus());
		Side side = order.getSide();
		JLabel line1 = line(sideLine(side, ORDER_TEXT_WIDTH, "item " + order.getItemId()), Color.WHITE);
		plugin.resolveItemName(order.getItemId(), name -> line1.setText(sideLine(side, ORDER_TEXT_WIDTH, name)));
		JLabel line2 = line(CoinAmounts.shortFormat(order.getLimitPrice()) + " each · " + (matched ? "in a deal" : "waiting"),
			ColorScheme.LIGHT_GRAY_COLOR);

		TextLink cancel = new TextLink("X", ColorScheme.LIGHT_GRAY_COLOR);
		cancel.setFont(FontManager.getRunescapeBoldFont());
		cancel.setBorder(new EmptyBorder(0, 6, 0, 2));
		cancel.setToolTipText(matched ? "Cancel this order and its deal" : "Cancel this order");
		cancel.onClick(() ->
		{
			cancel.setEnabled(false);
			ordersMessage.setText(" ");
			plugin.cancelOrder(order.getId());
		});

		Card card = new Card();
		card.setLayout(new BorderLayout(0, 0));
		card.setAlignmentX(LEFT_ALIGNMENT);
		JPanel top = transparent(new BorderLayout(8, 0));
		top.add(itemIcon(order.getItemId(), ROW_ICON_SIZE), BorderLayout.WEST);
		JPanel lines = verticalBox();
		lines.add(line1);
		lines.add(line2);
		top.add(lines, BorderLayout.CENTER);
		JPanel cancelBox = transparent(new BorderLayout());
		cancelBox.add(cancel, BorderLayout.NORTH);
		top.add(cancelBox, BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);

		JPanel progress = verticalBox();
		progress.setBorder(new EmptyBorder(6, 0, 0, 0));
		ThinBar bar = new ThinBar();
		bar.set(OrderProgress.fraction(order.getFilled(), order.getQuantity()), sideColor(side));
		bar.setAlignmentX(LEFT_ALIGNMENT);
		progress.add(bar);
		JLabel text = line(OrderProgress.text(side, order.getFilled(), order.getQuantity()), ColorScheme.LIGHT_GRAY_COLOR);
		text.setBorder(new EmptyBorder(3, 0, 0, 0));
		text.setAlignmentX(LEFT_ALIGNMENT);
		progress.add(text);
		card.add(progress, BorderLayout.SOUTH);
		return card;
	}

	/** Cancelling an order failed. Swing thread. */
	void onCancelFailed(String message)
	{
		ordersMessage.setText(wrap(escape(message)));
		setCancelConfirming(false); // the question stays open only while the cancel is in flight
	}

	// ---- deal card ----

	/** Show the active deal (replacing any shown), or the empty view when null. Swing thread. */
	void showDeal(@Nullable Deal newDeal)
	{
		boolean sameDeal = newDeal != null && deal != null && newDeal.getId() == deal.getId();
		deal = newDeal;
		plugin.dealChanged(newDeal);
		if (newDeal == null)
		{
			dealExpiresAt = null;
			setCancelConfirming(false);
			countdownTimer.stop();
			partnerAwayTimer.stop();
			refreshDealVisibility();
			// Whatever the status line said belonged to the deal that just ended, unless it is the ending itself.
			if (!endingShown)
			{
				dealStatus.setText(" ");
			}
			refreshDealTabLabel();
			revalidate();
			repaint();
			return;
		}
		endingShown = false;
		if (!sameDeal)
		{
			setCancelConfirming(false);
			worldFullPressedWorld = 0;
			dealStatus.setText(" ");
			showPartnerNearby(false);
			dealIcon.setIcon(null);
			AsyncBufferedImage image = itemManager.getImage(newDeal.getItemId());
			if (image != null)
			{
				image.addTo(dealIcon);
			}
			dealItemName = "item " + newDeal.getItemId();
			plugin.resolveItemName(newDeal.getItemId(), name ->
			{
				if (deal != null && deal.getId() == newDeal.getId())
				{
					dealItemName = name;
					dealHeadline.setText(headline(deal, name));
				}
			});
			// A new deal is the one thing to look at.
			setHistoryOpen(false);
			tabGroup.select(dealTab);
		}
		itemCard.strip(sideColor(newDeal.getMySide()));
		dealHeadline.setText(headline(newDeal, dealItemName));
		dealTotal.setText(totalLine(newDeal));
		dealPartner.setText("<html>" + escape(newDeal.getPartnerRsn()) + "</html>");
		showReputation(newDeal);
		dealExpiresAt = parseInstant(newDeal.getExpiresAt());
		Instant created = parseInstant(newDeal.getCreatedAt());
		dealTotalTime = dealExpiresAt != null && created != null ? Duration.between(created, dealExpiresAt)
			: MeetingClock.FALLBACK_TOTAL;
		dealMeet.setText(wrap("World " + newDeal.getWorld() + " · " + (newDeal.getMeetSpot() == null ? "" : escape(newDeal.getMeetSpot()))));
		dealCancel.setEnabled(true);
		refreshHop();
		updateCountdown();
		countdownTimer.start();
		refreshDealVisibility();
		refreshDealTabLabel();
		revalidate();
		repaint();
	}

	/** The deal with this id ended (cancelled, expired, ...): clear the card. Swing thread. */
	void dealEnded(long dealId, String status)
	{
		if (deal != null && deal.getId() != dealId)
		{
			return;
		}
		showDeal(null);
		// The ending stays on the Deal tab until the next deal or a logout; a refresh must not wipe it.
		endingShown = true;
		dealStatus.setText(wrap(escape(status)));
		plugin.loadHistory();
	}

	/** The world the client is on now (0 when logged out); Hop is enabled only away from the meeting world. Swing thread. */
	void setCurrentWorld(int world)
	{
		currentWorld = world;
		refreshStatus(); // "logged out" or "connecting" depends on being in the game
		refreshHop();
		buyForm.refreshAllButton();
		sellForm.refreshAllButton();
	}

	/**
	 * "Sell via Trade Finder" on an inventory item: select it on the Sell tab with the given
	 * quantity (the inventory count) and show that tab. Swing thread.
	 */
	void prefillSell(int itemId, String name, int quantity)
	{
		sellForm.selectItem(itemId, name);
		sellForm.setQuantity(quantity);
		sellForm.quantityDirty = true;
		setHistoryOpen(false);
		tabGroup.select(sellTab);
	}

	/**
	 * Hop and World full for the shown deal. On the meeting world the Hop button says so instead of
	 * sitting there greyed out without a reason, and World full is off: this player got in.
	 */
	private void refreshHop()
	{
		boolean onWorld = deal != null && currentWorld > 0 && currentWorld == deal.getWorld();
		hopButton.setEnabled(deal != null && currentWorld > 0 && !onWorld);
		hopButton.setText(onWorld ? "You are here" : "Hop");
		hopButton.setToolTipText(deal == null ? null
			: onWorld ? "You are on world " + deal.getWorld() + ", the meeting world"
			: "Hop to world " + deal.getWorld());
		worldFullButton.setEnabled(deal != null && !onWorld && deal.getWorld() != worldFullPressedWorld);
		worldFullButton.setToolTipText(onWorld ? "You are already on the meeting world"
			: "The meeting world is full: move the deal to the next backup world");
	}

	/** A line under the card about the current deal, e.g. after a declined trade. Swing thread. */
	void setDealStatus(String status)
	{
		if (deal == null)
		{
			return; // a late answer (e.g. the trade report) must not outlive its deal
		}
		dealStatus.setText("<html>" + escape(status) + "</html>"); // plain text in: it may name the partner or quote the server
	}

	/** Whether this account has membership right now (from the client). Swing thread. */
	void setMember(boolean member)
	{
		this.member = member;
		buyForm.refreshAvailability();
		sellForm.refreshAvailability();
	}

	/** {@code POST /deals/{id}/world-full} failed, e.g. "No backup world left." Swing thread. */
	void onWorldFullFailed(String message)
	{
		worldFullPressedWorld = 0;
		refreshHop();
		setDealStatus(message);
	}

	/** The meeting moved to another world: the partner has to be seen there again. Swing thread. */
	void dealWorldChanged()
	{
		showPartnerNearby(false);
		partnerAwayTimer.stop();
	}

	/** {@code deal.partner_nearby}: the partner is on the meeting world at the GE. Swing thread. */
	void setPartnerNearby(long dealId)
	{
		if (deal == null || deal.getId() != dealId)
		{
			return;
		}
		showPartnerNearby(true);
		partnerAwayTimer.restart();
	}

	/** The meeting world of the shown deal, or 0. Swing thread. */
	int dealWorld()
	{
		return deal == null ? 0 : deal.getWorld();
	}

	private void showPartnerNearby(boolean nearby)
	{
		if (nearby)
		{
			partnerPill.show("at the GE", OK_GREEN);
		}
		else
		{
			partnerPill.show("not seen yet", ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	/** "12 deals · 1 no-show · 2 cancels" for the partner; no-shows and cancels turn red above zero. */
	private void showReputation(Deal shown)
	{
		Reputation rep = shown.getPartner();
		dealReputation.setVisible(rep != null);
		if (rep == null)
		{
			return;
		}
		dealReputation.setText("<html>" + plural(rep.getDealsDone(), "deal") + " · " + flagged(rep.getNoShows(), "no-show")
			+ " · " + flagged(rep.getCancels(), "cancel") + "</html>");
	}

	private static String flagged(int n, String noun)
	{
		String text = plural(n, noun);
		return n > 0 ? "<font color='" + RED_HEX + "'>" + text + "</font>" : text;
	}

	private void refreshDealTabLabel()
	{
		if (dealTab == null)
		{
			return;
		}
		boolean dot = deal != null && !dealTab.isSelected();
		dealTab.setText(dot ? "<html>Deal <font color='" + ORANGE_HEX + "'>●</font></html>" : "Deal");
	}

	private static String headline(Deal deal, String itemName)
	{
		return sideLine(deal.getMySide(), ITEM_TEXT_WIDTH, CoinAmounts.format(deal.getQuantity()) + " " + itemName);
	}

	/** "4,500 gp · 1,500 each" (just the total for one item). */
	private static String totalLine(Deal deal)
	{
		long total = deal.getPrice() * deal.getQuantity();
		StringBuilder text = new StringBuilder(CoinAmounts.format(total)).append(" gp");
		if (deal.getQuantity() > 1)
		{
			text.append(" · ").append(CoinAmounts.format(deal.getPrice())).append(" each");
		}
		if (total > CoinAmounts.MAX_STACK)
		{
			text.append(" in Platinum tokens");
		}
		return text.toString();
	}

	private void updateCountdown()
	{
		if (deal == null)
		{
			return;
		}
		refreshCancelWarning(); // the text changes when the free-cancel time passes
		if (dealExpiresAt == null)
		{
			dealClock.setText("expires " + deal.getExpiresAt());
			dealClock.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			clockBar.set(0, ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}
		// The server never expires a deal while its trade screen is open; otherwise it closes within seconds.
		Duration left = Duration.between(Instant.now(), dealExpiresAt);
		Color color = MeetingClock.isUrgent(left) ? WARN_RED : WARN_YELLOW;
		dealClock.setText(MeetingClock.text(left));
		dealClock.setForeground(color);
		clockBar.set(MeetingClock.fraction(left, dealTotalTime), color);
	}

	/** The backend sends ISO-8601 with an offset ("...Z" or "+00:00"); a naive timestamp is taken as UTC. */
	@Nullable
	private static Instant parseInstant(@Nullable String text)
	{
		if (text == null)
		{
			return null;
		}
		try
		{
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException e)
		{
			try
			{
				return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
			}
			catch (DateTimeParseException ignored)
			{
				return null;
			}
		}
	}

	// ---- session state ----

	void setBackendEnabled(boolean enabled)
	{
		backendEnabled = enabled;
		if (!enabled)
		{
			registered = false;
			registrationDown = false;
			socketDown = false;
			blocked = false;
			forgetPosting();
			showOrders(List.of());
			showDeal(null);
			clearMe();
			endingShown = false;
			dealStatus.setText(" ");
		}
		refreshStatus();
	}

	/** Session registered as {@code rsn}, or lost (null). Swing thread. */
	void setRegistered(@Nullable String rsn)
	{
		registered = rsn != null;
		registeredRsn = rsn;
		blocked = false;
		registrationDown = false;
		socketDown = false;
		if (!registered)
		{
			live = false;
			forgetPosting();
			showOrders(List.of());
			showDeal(null);
			clearMe();
			endingShown = false;
			dealStatus.setText(" ");
		}
		refreshStatus();
	}

	private boolean unreachable()
	{
		return backendEnabled && (registrationDown || socketDown);
	}

	/** The server refuses this account (socket close 4403): one line, and no more connecting. Swing thread. */
	void setBlocked()
	{
		blocked = true;
		live = false;
		socketDown = false;
		buyForm.refreshAvailability();
		sellForm.refreshAvailability();
		refreshStatus();
	}

	/** The WebSocket keeps failing to reconnect (true) or is fine (false). Swing thread. */
	void setSocketDown(boolean down)
	{
		if (socketDown != down)
		{
			socketDown = down;
			refreshStatus();
		}
	}

	/** The card of a deal is hidden while the server cannot be reached: it would be stale. */
	private void refreshDealVisibility()
	{
		boolean hide = unreachable();
		dealBody.setVisible(deal != null && !hide);
		dealEmptyBox.setVisible(deal == null && !hide);
	}

	/** The WebSocket is up (events arrive live) or down (reconnecting). Swing thread. */
	void setLive(boolean live)
	{
		this.live = live;
		buyForm.refreshAvailability();
		sellForm.refreshAvailability();
		refreshStatus();
	}

	/** Registration failed; shown until the next attempt. Swing thread. */
	void setRegistrationError(String message)
	{
		if (TradeFinderApi.isUnreachable(message))
		{
			registrationDown = true;
			refreshStatus();
			return;
		}
		registrationDown = false;
		refreshStatus();
		setNotice(message, WARN_RED);
	}

	private void setNotice(@Nullable String text, Color color)
	{
		noticeLabel.setForeground(color);
		noticeLabel.setText(text == null ? "" : wrap(escape(text)));
		noticeLabel.setVisible(text != null);
		revalidate();
		repaint();
	}

	private void refreshStatus()
	{
		buyForm.setRegistered(registered);
		sellForm.setRegistered(registered);
		ConnectionState state = ConnectionState.of(backendEnabled, registered, live, currentWorld > 0, unreachable());
		Color dot;
		switch (state)
		{
			case CONNECTED:
				dot = OK_GREEN;
				break;
			case CONNECTING:
				dot = WARN_YELLOW;
				break;
			case OFF:
			case UNREACHABLE:
				dot = WARN_RED;
				break;
			default:
				dot = ColorScheme.LIGHT_GRAY_COLOR;
		}
		statusDot.set(dot, state.tooltip(registeredRsn));
		refreshDealVisibility();
		if (state == ConnectionState.OFF)
		{
			setNotice("Turn on \"Connect to Trade Finder server\" in the plugin settings.", WARN_YELLOW);
			return;
		}
		if (blocked)
		{
			setNotice("This account is blocked from Trade Finder.", WARN_RED);
			return;
		}
		if (state == ConnectionState.UNREACHABLE)
		{
			setNotice(TradeFinderApi.UNREACHABLE + " Trying again...", WARN_RED);
			return;
		}
		setNotice(null, WARN_RED);
	}

	/** Stop the timers. Called from the plugin on shutdown. */
	void dispose()
	{
		buyForm.dispose();
		sellForm.dispose();
		countdownTimer.stop();
		partnerAwayTimer.stop();
		blockTimer.stop();
	}
}
