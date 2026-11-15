package com.tradefinder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Green "matches the deal" or red "differs" box next to the trade window. It renders the last
 * {@link TradeCheck.Result} computed by {@link TradeTracker} on container changes; nothing is
 * compared per frame.
 */
class TradeCheckOverlay extends Overlay
{
	private static final Color OK_GREEN = new Color(100, 220, 100);
	private static final Color WARN_RED = new Color(255, 90, 90);
	private static final Color OK_BACKGROUND = new Color(20, 70, 20, 200);
	private static final Color WARN_BACKGROUND = new Color(90, 20, 20, 200);
	private static final int GAP = 4;
	private static final int PANEL_WIDTH = 240;
	private static final int BORDER_WIDTH = 4;
	private static final int WARNING_PADDING = 5;
	private static final Stroke BORDER_STROKE = new BasicStroke(BORDER_WIDTH);
	private static final int BORDER_ALPHA_MIN = 130;
	private static final int BORDER_ALPHA_STEPS = 126;
	/** The pulse border colours, built once. */
	private static final Color[] BORDER_COLORS = new Color[BORDER_ALPHA_STEPS];
	private static final Color WARNING_FILL = new Color(150, 0, 0, 235);
	private static final Color WARNING_DETAIL = new Color(255, 200, 200);

	static
	{
		for (int i = 0; i < BORDER_ALPHA_STEPS; i++)
		{
			BORDER_COLORS[i] = new Color(255, 40, 40, BORDER_ALPHA_MIN + i);
		}
	}

	private final Client client;
	private final TradeTracker tracker;
	private final PanelComponent panel = new PanelComponent();

	/** Height of the last render, so the box can sit above the window on the next frame. */
	private int lastHeight;

	/** Warning text is rebuilt only when the check result changes, not every frame. */
	private TradeCheck.Result warningFor;
	private List<String> warningLines = Collections.emptyList();

	TradeCheckOverlay(Plugin plugin, Client client, TradeTracker tracker)
	{
		super(plugin);
		this.client = client;
		this.tracker = tracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
		panel.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TradeCheck.Result result = tracker.lastCheck();
		if (result == null)
		{
			return null;
		}
		Rectangle window = tradeWindowBounds();
		if (window == null)
		{
			return null;
		}

		panel.getChildren().clear();
		boolean ok = result.matches();
		panel.setBackgroundColor(ok ? OK_BACKGROUND : WARN_BACKGROUND);
		panel.getChildren().add(TitleComponent.builder()
			.text(ok ? "Trade Finder: matches the deal" : "Trade Finder: differs from the deal")
			.color(ok ? OK_GREEN : WARN_RED)
			.build());
		panel.getChildren().add(LineComponent.builder().left(result.getYouOffer()).leftColor(Color.WHITE).build());
		panel.getChildren().add(LineComponent.builder().left(result.getYouGet()).leftColor(Color.WHITE).build());
		addSide(result.getMine());
		addSide(result.getTheirs());

		// Above the window when there is room, otherwise below it.
		int x = window.x;
		int y = window.y - lastHeight - GAP;
		if (y < 0)
		{
			y = window.y + window.height + GAP;
		}
		graphics.translate(x, y);
		Dimension size = panel.render(graphics);
		graphics.translate(-x, -y);
		lastHeight = size == null ? 0 : size.height;

		if (!ok && TradeAlert.isAlert(result))
		{
			drawBorder(graphics, new Rectangle(x, y, size == null ? PANEL_WIDTH : size.width, lastHeight));
			drawAcceptWarning(graphics, result);
		}
		return null;
	}

	/** Thick red border that pulses; drawn over the box only, never over the trade window. */
	private void drawBorder(Graphics2D graphics, Rectangle box)
	{
		int step = (int) ((BORDER_ALPHA_STEPS - 1) * TradeAlert.pulse(System.currentTimeMillis()));
		graphics.setColor(BORDER_COLORS[step]);
		Stroke old = graphics.getStroke();
		graphics.setStroke(BORDER_STROKE);
		graphics.drawRect(box.x - BORDER_WIDTH / 2, box.y - BORDER_WIDTH / 2,
			box.width + BORDER_WIDTH, box.height + BORDER_WIDTH);
		graphics.setStroke(old);
	}

	/** Red warning next to the Accept button of whichever trade screen is open. Draw only: no clicks are touched. */
	private void drawAcceptWarning(Graphics2D graphics, TradeCheck.Result result)
	{
		Widget accept = visible(InterfaceID.Trademain.ACCEPT);
		if (accept == null)
		{
			accept = visible(InterfaceID.Tradeconfirm.TRADE2ACCEPT);
		}
		if (accept == null)
		{
			return;
		}
		if (result != warningFor)
		{
			warningFor = result;
			warningLines = TradeAlert.warningLines(result);
		}
		Rectangle button = accept.getBounds();
		FontMetrics metrics = graphics.getFontMetrics();
		int lineHeight = metrics.getHeight();
		int textWidth = 0;
		for (String line : warningLines)
		{
			textWidth = Math.max(textWidth, metrics.stringWidth(line));
		}
		int width = textWidth + 2 * WARNING_PADDING;
		int height = lineHeight * warningLines.size() + 2 * WARNING_PADDING;
		int x = Math.max(0, button.x + button.width / 2 - width / 2);
		int y = button.y + button.height + GAP;
		if (y + height > client.getCanvasHeight())
		{
			y = Math.max(0, button.y - height - GAP);
		}
		graphics.setColor(WARNING_FILL);
		graphics.fillRect(x, y, width, height);
		graphics.setColor(Color.RED);
		graphics.drawRect(x, y, width, height);
		int baseline = y + WARNING_PADDING + metrics.getAscent();
		for (int i = 0; i < warningLines.size(); i++)
		{
			graphics.setColor(i == 0 ? Color.WHITE : WARNING_DETAIL);
			graphics.drawString(warningLines.get(i), x + WARNING_PADDING, baseline + i * lineHeight);
		}
	}

	private Widget visible(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget == null || widget.isHidden() ? null : widget;
	}

	private void addSide(TradeCheck.SideResult side)
	{
		boolean ok = side.matches();
		panel.getChildren().add(LineComponent.builder()
			.left(side.getWho())
			.right(side.getOffered())
			.leftColor(Color.WHITE)
			.rightColor(ok ? OK_GREEN : WARN_RED)
			.build());
		for (String problem : side.getProblems())
		{
			panel.getChildren().add(LineComponent.builder()
				.left(problem)
				.leftColor(WARN_RED)
				.build());
		}
	}

	private Rectangle tradeWindowBounds()
	{
		Widget frame = client.getWidget(InterfaceID.Trademain.FRAME);
		if (frame == null || frame.isHidden())
		{
			frame = client.getWidget(InterfaceID.Tradeconfirm.FRAME);
		}
		if (frame == null || frame.isHidden())
		{
			return null;
		}
		return frame.getBounds();
	}
}
