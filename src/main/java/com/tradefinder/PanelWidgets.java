package com.tradefinder;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The small painted pieces of the side panel: rounded cards, pills, thin bars, the status dot, text
 * links and the filled Post button. All painting is a few shapes on repaint only, nothing per frame.
 */
final class PanelWidgets
{
	static final int RADIUS = 8;

	private PanelWidgets()
	{
	}

	private static void smooth(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	}

	/** A rounded dark card; optional 2 px strip along the top, hover colour, or a dashed outline instead of a fill. */
	static class Card extends JPanel
	{
		@Nullable
		private Color strip;
		private boolean dashed;
		private boolean hoverable;
		private boolean hover;

		Card()
		{
			setOpaque(false);
			setBorder(new EmptyBorder(8, 8, 8, 8));
		}

		Card strip(@Nullable Color color)
		{
			this.strip = color;
			return this;
		}

		Card dashed()
		{
			this.dashed = true;
			return this;
		}

		/** Lighten while the mouse is over the card (children without mouse listeners pass events up). */
		Card hoverable()
		{
			hoverable = true;
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
			return this;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			smooth(g);
			int w = getWidth();
			int h = getHeight();
			if (dashed)
			{
				g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
				g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
				g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, RADIUS, RADIUS));
			}
			else
			{
				RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w, h, RADIUS, RADIUS);
				g.setColor(hoverable && hover ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
				g.fill(shape);
				if (strip != null)
				{
					g.clip(shape);
					g.setColor(strip);
					g.fillRect(0, 0, w, 2);
				}
			}
			g.dispose();
		}
	}

	/** A small rounded label: coloured text on a faint tint of the same colour. */
	static class Pill extends JLabel
	{
		private Color tint = ColorScheme.LIGHT_GRAY_COLOR;

		Pill()
		{
			setOpaque(false);
			setFont(FontManager.getRunescapeSmallFont());
			setBorder(new EmptyBorder(0, 6, 0, 6));
		}

		void show(String text, Color color)
		{
			setText(text);
			setForeground(color);
			tint = new Color(color.getRed(), color.getGreen(), color.getBlue(), 45);
			setVisible(!text.isEmpty());
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			smooth(g);
			g.setColor(tint);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	/** A thin rounded bar: a faint track and a coloured fill for {@code fraction} of the width. */
	static class ThinBar extends JComponent
	{
		private static final int HEIGHT = 4;
		private double fraction;
		private Color color = ColorScheme.BRAND_ORANGE;

		ThinBar()
		{
			setPreferredSize(new Dimension(10, HEIGHT));
			setMinimumSize(new Dimension(10, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		}

		void set(double fraction, Color color)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.color = color;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			smooth(g);
			g.setColor(new Color(255, 255, 255, 28));
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), HEIGHT, HEIGHT, HEIGHT));
			int fill = (int) Math.round(getWidth() * fraction);
			if (fill > 0)
			{
				g.setColor(color);
				g.fill(new RoundRectangle2D.Float(0, 0, Math.max(fill, HEIGHT), HEIGHT, HEIGHT, HEIGHT));
			}
			g.dispose();
		}
	}

	/** The round connection dot of the header. */
	static class StatusDot extends JComponent
	{
		private static final int SIZE = 10;
		private Color color = ColorScheme.LIGHT_GRAY_COLOR;

		StatusDot()
		{
			Dimension size = new Dimension(SIZE + 2, SIZE + 2);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
		}

		void set(Color color, String tooltip)
		{
			this.color = color;
			setToolTipText(tooltip);
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			smooth(g);
			g.setColor(color);
			g.fillOval(1, 1, SIZE, SIZE);
			g.dispose();
		}
	}

	/** A clickable text label: grey, white on hover, optional highlight colour while "active". */
	static class TextLink extends JLabel
	{
		private final Color idle;
		private Color shown;
		private boolean active;
		private boolean hover;
		@Nullable
		private Runnable action;

		TextLink(String text, Color idle)
		{
			super(text);
			this.idle = idle;
			this.shown = idle;
			setFont(FontManager.getRunescapeSmallFont());
			setForeground(idle);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (isEnabled() && action != null)
					{
						action.run();
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					recolor();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					recolor();
				}
			});
		}

		TextLink onClick(Runnable action)
		{
			this.action = action;
			return this;
		}

		/** Orange while the thing it opens is open. */
		void setActive(boolean active)
		{
			this.active = active;
			recolor();
		}

		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			recolor();
		}

		private void recolor()
		{
			if (!isEnabled())
			{
				shown = ColorScheme.MEDIUM_GRAY_COLOR;
			}
			else if (active)
			{
				shown = ColorScheme.BRAND_ORANGE;
			}
			else
			{
				shown = hover ? Color.WHITE : idle;
			}
			setForeground(shown);
		}
	}

	/** The Post button: filled in the side colour with dark text. */
	static class FilledButton extends JButton
	{
		private final Color fill;

		FilledButton(String text, Color fill)
		{
			super(text);
			this.fill = fill;
			setFont(FontManager.getRunescapeBoldFont());
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setBorder(new EmptyBorder(6, 8, 6, 8));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			smooth(g);
			boolean on = isEnabled();
			Color base = on ? fill : ColorScheme.MEDIUM_GRAY_COLOR;
			if (on && getModel().isRollover())
			{
				base = base.brighter();
			}
			if (on && getModel().isPressed())
			{
				base = base.darker();
			}
			g.setColor(base);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			g.setColor(on ? ColorScheme.DARKER_GRAY_COLOR.darker() : ColorScheme.LIGHT_GRAY_COLOR);
			int x = (getWidth() - fm.stringWidth(getText())) / 2;
			int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			g.drawString(getText(), x, y);
			g.dispose();
		}
	}
}
