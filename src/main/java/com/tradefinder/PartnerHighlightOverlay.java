package com.tradefinder;

import com.tradefinder.api.Deal;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Orange outline, arrow and name tag on the deal partner. The partner reference is maintained by
 * {@link TradeTracker} from spawn events; this overlay only draws it.
 */
class PartnerHighlightOverlay extends Overlay
{
	private static final Color ORANGE = new Color(255, 140, 0);
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;
	private static final int LABEL_HEIGHT_OFFSET = 40;
	private static final int ARROW_HALF_WIDTH = 6;
	private static final int ARROW_HEIGHT = 8;

	private final TradeTracker tracker;
	private final ModelOutlineRenderer outlineRenderer;

	PartnerHighlightOverlay(Plugin plugin, TradeTracker tracker, ModelOutlineRenderer outlineRenderer)
	{
		super(plugin);
		this.tracker = tracker;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Player partner = tracker.partner();
		Deal deal = tracker.deal();
		if (partner == null || deal == null)
		{
			return null;
		}
		outlineRenderer.drawOutline(partner, OUTLINE_WIDTH, ORANGE, OUTLINE_FEATHER);

		String label = deal.getPartnerRsn() + " - trade partner";
		Point text = partner.getCanvasTextLocation(graphics, label, partner.getLogicalHeight() + LABEL_HEIGHT_OFFSET);
		if (text == null)
		{
			return null;
		}
		OverlayUtil.renderTextLocation(graphics, text, label, ORANGE);

		// A small arrow under the label pointing down at the player.
		int width = graphics.getFontMetrics().stringWidth(label);
		int centreX = text.getX() + width / 2;
		int top = text.getY() + 4;
		Polygon arrow = new Polygon(
			new int[]{centreX - ARROW_HALF_WIDTH, centreX + ARROW_HALF_WIDTH, centreX},
			new int[]{top, top, top + ARROW_HEIGHT},
			3);
		Object antialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.BLACK);
		graphics.drawPolygon(arrow);
		graphics.setColor(ORANGE);
		graphics.fillPolygon(arrow);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			antialias == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : antialias);
		return null;
	}
}
