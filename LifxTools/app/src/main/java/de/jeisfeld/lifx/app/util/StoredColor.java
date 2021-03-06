package de.jeisfeld.lifx.app.util;

import java.util.List;

import de.jeisfeld.lifx.app.R;
import de.jeisfeld.lifx.lan.Light;
import de.jeisfeld.lifx.lan.type.Color;

/**
 * Class holding information about a stored color.
 */
public class StoredColor {
	/**
	 * The color.
	 */
	private final Color mColor;
	/**
	 * The deviceId for the color.
	 */
	private final int mDeviceId;
	/**
	 * The name of the color.
	 */
	private final String mName;
	/**
	 * The id for storage.
	 */
	private final int mId;

	/**
	 * Generate a stored color.
	 *
	 * @param id The id for storage
	 * @param color The color
	 * @param deviceId The device id
	 * @param name The name
	 */
	public StoredColor(final int id, final Color color, final int deviceId, final String name) {
		mColor = color;
		mDeviceId = deviceId;
		mName = name;
		mId = id;
	}

	/**
	 * Generate a new stored color without id.
	 *
	 * @param color The color
	 * @param deviceId The device id
	 * @param name The name
	 */
	public StoredColor(final Color color, final int deviceId, final String name) {
		this(-1, color, deviceId, name);
	}

	/**
	 * Generate a new stored color by adding id.
	 *
	 * @param id The id
	 * @param storedColor the base stored color.
	 */
	public StoredColor(final int id, final StoredColor storedColor) {
		this(id, storedColor.getColor(), storedColor.getDeviceId(), storedColor.getName());
	}

	/**
	 * Retrieve a stored color from storage via id.
	 *
	 * @param colorId The id.
	 */
	protected StoredColor(final int colorId) {
		mId = colorId;
		mName = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_color_name, colorId);
		mDeviceId = PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_color_device_id, colorId, -1);
		mColor = PreferenceUtil.getIndexedSharedPreferenceColor(R.string.key_color_color, colorId, Color.NONE);
	}

	/**
	 * Retrieve a stored color from storage via id.
	 *
	 * @param colorId The id.
	 * @return The stored color.
	 */
	public static StoredColor fromId(final int colorId) {
		boolean isMultiZone = PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_color_multizone_type, colorId, -1) >= 0;
		boolean isTileChain = PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_color_tilechain_type, colorId, -1) >= 0;

		if (isMultiZone) {
			return new StoredMultizoneColors(colorId);
		}
		else if (isTileChain) {
			return new StoredTileColors(colorId);
		}
		else {
			return new StoredColor(colorId);
		}
	}

	/**
	 * Store this color.
	 *
	 * @return the stored color.
	 */
	public StoredColor store() {
		StoredColor storedColor = this;
		if (getId() < 0) {
			int newId = PreferenceUtil.getSharedPreferenceInt(R.string.key_color_max_id, 0) + 1;
			PreferenceUtil.setSharedPreferenceInt(R.string.key_color_max_id, newId);

			List<Integer> colorIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_color_ids);
			colorIds.add(newId);
			PreferenceUtil.setSharedPreferenceIntList(R.string.key_color_ids, colorIds);
			storedColor = new StoredColor(newId, this);
		}
		PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_color_device_id, storedColor.getId(), storedColor.getDeviceId());
		PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_color_name, storedColor.getId(), storedColor.getName());
		PreferenceUtil.setIndexedSharedPreferenceColor(R.string.key_color_color, storedColor.getId(), storedColor.getColor());
		return storedColor;
	}

	/**
	 * Get the color.
	 *
	 * @return The color.
	 */
	public Color getColor() {
		return mColor;
	}

	/**
	 * Get the device id for the color.
	 *
	 * @return The device id for the color.
	 */
	public int getDeviceId() {
		return mDeviceId;
	}

	/**
	 * Get the light for the color.
	 *
	 * @return The light for the color.
	 */
	public Light getLight() {
		return (Light) DeviceRegistry.getInstance().getDeviceById(getDeviceId());
	}

	/**
	 * Get the name of the color.
	 *
	 * @return The name of the color.
	 */
	public String getName() {
		return mName;
	}

	/**
	 * Get the id for storage.
	 *
	 * @return The id for storage.
	 */
	public int getId() {
		return mId;
	}

	// OVERRIDABLE
	@Override
	public String toString() {
		return "[" + getId() + "](" + getName() + ")(" + (getLight() == null ? getDeviceId() : getLight().getLabel() + ")-" + getColor());
	}
}
