package wc42;

/**
 * Health sheet {@code textures/gui/health.png} is 123x18.
 * Each cross is 18x18, columns 21 pixels apart.
 * Food sheet {@code textures/gui/food.png} is 123x18.
 * Each bottle cell is 16x18, columns 16 pixels apart.
 */
public final class StatusIcons {
	public static final int HEALTH_SPRITE = 18;
	public static final int HEALTH_STRIDE = 21;
	public static final int HEALTH_SHEET_WIDTH = 123;
	public static final int HEALTH_SHEET_HEIGHT = 18;

	public static final int FOOD_SPRITE_WIDTH = 16;
	public static final int FOOD_SPRITE_HEIGHT = 18;
	public static final int FOOD_STRIDE = 16;
	public static final int FOOD_SHEET_WIDTH = 123;
	public static final int FOOD_SHEET_HEIGHT = 18;

	public static final int FULL = 0;
	public static final int HALF = 1;
	public static final int YELLOW = 2;
	public static final int RED_HALF = 3;
	public static final int EMPTY_RED = 4;
	public static final int EMPTY_WHITE = 5;

	public static final long BLINK_PERIOD_MS = 500L;
	public static final int FOOD_MAX = 20;

	private StatusIcons() {
	}

	/**
	 * Ratio is current / max.
	 * 80% full, 55% half white, 35% yellow, 15% red. Below 15% blinks.
	 * Food turns red under 35%, which is when sprint is no longer possible.
	 */
	public static int column(float ratio, long timeMs) {
		if (ratio >= 0.80f) {
			return FULL;
		}
		if (ratio >= 0.55f) {
			return HALF;
		}
		if (ratio >= 0.35f) {
			return YELLOW;
		}
		if (ratio >= 0.15f) {
			return RED_HALF;
		}
		boolean redFrame = (Math.floorDiv(timeMs, BLINK_PERIOD_MS) & 1L) == 0L;
		return redFrame ? EMPTY_RED : EMPTY_WHITE;
	}
}
