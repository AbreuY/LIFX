package de.jeisfeld.lifx.app.util;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;
import de.jeisfeld.lifx.app.Application;
import de.jeisfeld.lifx.app.R;
import de.jeisfeld.lifx.lan.Device;
import de.jeisfeld.lifx.lan.LifxLan;
import de.jeisfeld.lifx.lan.LifxLanConnection.RetryPolicy;
import de.jeisfeld.lifx.lan.Light;
import de.jeisfeld.lifx.lan.MultiZoneLight;
import de.jeisfeld.lifx.lan.TileChain;
import de.jeisfeld.lifx.lan.message.ResponseMessage;
import de.jeisfeld.lifx.lan.message.StateService;
import de.jeisfeld.lifx.lan.type.Product;
import de.jeisfeld.lifx.lan.type.Vendor;
import de.jeisfeld.lifx.os.OsTools;

/**
 * A registry holding information about devices.
 */
public final class DeviceRegistry {
	/**
	 * The default port.
	 */
	private static final int DEFAULT_PORT = 56700;
	/**
	 * Device parameter for device id.
	 */
	public static final String DEVICE_ID = "deviceId";
	/**
	 * Device parameter for "show" flag.
	 */
	public static final String DEVICE_PARAMETER_SHOW = "showDevice";

	/**
	 * The singleton instance of DeviceRegistry.
	 */
	private static DeviceRegistry mInstance = null;
	/**
	 * The devices.
	 */
	private final SparseArray<Device> mDevices = new SparseArray<>();
	/**
	 * A map from MAC address to device id.
	 */
	private final Map<String, Integer> mMacToIdMap = new HashMap<>();

	/**
	 * The source id.
	 */
	private final int mSourceId;

	/**
	 * Create the device registry and retrieve stored entries.
	 */
	private DeviceRegistry() {
		mSourceId = OsTools.getPid();
		List<Integer> deviceIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_device_ids);

		for (int deviceId : deviceIds) {
			String mac = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_device_mac, deviceId);
			InetAddress inetAddress = null;
			try {
				inetAddress = InetAddress.getByAddress(
						PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_device_address, deviceId).getBytes(StandardCharsets.ISO_8859_1));
			}
			catch (Exception e) {
				Log.w(Application.TAG, e);
			}
			int port = PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_port, deviceId, DeviceRegistry.DEFAULT_PORT);
			String label = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_device_label, deviceId);
			if (label == null) {
				label = "???";
			}
			DeviceType type = DeviceType.fromOrdinal(PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_type, deviceId, 0));
			Vendor vendor = Vendor.fromInt(PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_vendor, deviceId, 0));
			Product product = Product.fromId(PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_product, deviceId, 0));
			int version = PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_product, deviceId, 0);
			byte zoneCount = (byte) PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_zone_count, deviceId, -1);
			long buildTimestamp = PreferenceUtil.getIndexedSharedPreferenceLong(R.string.key_device_build_timestamp, deviceId, -1);
			byte tileCount = (byte) PreferenceUtil.getIndexedSharedPreferenceInt(R.string.key_device_tile_count, deviceId, -1);

			mMacToIdMap.put(mac, deviceId);

			Device device = createDevice(type, mac, inetAddress, port, vendor, product, version, label, zoneCount, tileCount, buildTimestamp);
			device.setParameter(DEVICE_ID, deviceId);
			device.setParameter(DEVICE_PARAMETER_SHOW, PreferenceUtil.getIndexedSharedPreferenceBoolean(R.string.key_device_show, deviceId, true));
			mDevices.put(deviceId, device);
		}

	}

	/**
	 * Create a device instance from stored data.
	 *
	 * @param type           The device type.
	 * @param mac            The MAC.
	 * @param inetAddress    The internet address.
	 * @param port           The port.
	 * @param vendor         The vendor.
	 * @param product        The product.
	 * @param version        The version.
	 * @param label          The label.
	 * @param zoneCount      The number of zones of Multizone device.
	 * @param tileCount      The number of tiles of TileChain.
	 * @param buildTimestamp The build timestamp of the firmware.
	 */
	private Device createDevice(final DeviceType type, final String mac, final InetAddress inetAddress, final int port, // SUPPRESS_CHECKSTYLE
								final Vendor vendor, final Product product, final int version, final String label, final byte zoneCount,
								final byte tileCount, final long buildTimestamp) {
		if (type == DeviceType.DEVICE) {
			return new Device(mac, inetAddress, port, mSourceId, vendor, product, version, label);
		}
		else if (type == DeviceType.MULTIZONE) {
			return new MultiZoneLight(mac, inetAddress, port, mSourceId, vendor, product, version, label, zoneCount, buildTimestamp);
		}
		else if (type == DeviceType.TILECHAIN) {
			return new TileChain(mac, inetAddress, port, mSourceId, vendor, product, version, label, tileCount);
		}
		else {
			return new Light(mac, inetAddress, port, mSourceId, vendor, product, version, label);
		}
	}

	/**
	 * Get the list of known devices.
	 *
	 * @param onlyFlagged Get only devices which are flagged.
	 * @return The list of known devices.
	 */
	public List<Device> getDevices(final boolean onlyFlagged) {
		List<Device> result = new ArrayList<>();
		for (int deviceId : PreferenceUtil.getSharedPreferenceIntList(R.string.key_device_ids)) {
			Device device = mDevices.get(deviceId);
			if (device != null && !(onlyFlagged && Boolean.FALSE.equals(device.getParameter(DEVICE_PARAMETER_SHOW)))) {
				result.add(device);
			}
		}
		return result;
	}

	/**
	 * Get a known device by its storage id.
	 *
	 * @param id the storage id
	 * @return The device
	 */
	public Device getDeviceById(final int id) {
		return mDevices.get(id);
	}

	/**
	 * Add or update a device in local store.
	 *
	 * @param device the device
	 */
	public void addOrUpdate(final Device device) {
		if (!mMacToIdMap.containsKey(device.getTargetAddress())) {
			// new device
			int newId = PreferenceUtil.getSharedPreferenceInt(R.string.key_device_max_id, 0) + 1;
			PreferenceUtil.setSharedPreferenceInt(R.string.key_device_max_id, newId);

			List<Integer> deviceIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_device_ids);
			deviceIds.add(newId);
			PreferenceUtil.setSharedPreferenceIntList(R.string.key_device_ids, deviceIds);
			mMacToIdMap.put(device.getTargetAddress(), newId);
		}
		int deviceId = mMacToIdMap.get(device.getTargetAddress());

		String label = device.getLabel();
		if (label == null) {
			label = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_device_label, deviceId);
			if (label == null) {
				device.storeLabel("???");
			}
			else {
				device.storeLabel(label);
			}
		}

		device.setParameter(DEVICE_ID, deviceId);
		device.setParameter(DEVICE_PARAMETER_SHOW, PreferenceUtil.getIndexedSharedPreferenceBoolean(R.string.key_device_show, deviceId, true));
		mDevices.put(deviceId, device);

		PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_device_mac, deviceId, device.getTargetAddress());
		PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_device_address, deviceId,
				new String(device.getInetAddress().getAddress(), StandardCharsets.ISO_8859_1));
		PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_port, deviceId, device.getPort());
		if (label != null) {
			PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_device_label, deviceId, label);
		}
		PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_type, deviceId, DeviceType.fromDevice(device).ordinal());
		PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_vendor, deviceId, device.getVendor().value());
		PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_product, deviceId, device.getProduct().getId());
		PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_version, deviceId, device.getVersion());
		if (device instanceof MultiZoneLight) {
			PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_zone_count, deviceId, ((MultiZoneLight) device).getZoneCount());
			PreferenceUtil.setIndexedSharedPreferenceLong(R.string.key_device_build_timestamp, deviceId, device.getFirmwareBuildTime().getTime());
		}
		if (device instanceof TileChain) {
			PreferenceUtil.setIndexedSharedPreferenceInt(R.string.key_device_tile_count, deviceId, ((TileChain) device).getTileCount());
		}
	}

	/**
	 * Remove a device from local store.
	 *
	 * @param device The device to be deleted.
	 */
	public void remove(final Device device) {
		Integer deviceId = mMacToIdMap.get(device.getTargetAddress());
		if (deviceId == null) {
			return;
		}
		mMacToIdMap.remove(device.getTargetAddress());
		mDevices.remove(deviceId);

		List<Integer> deviceIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_device_ids);
		deviceIds.remove(deviceId);
		PreferenceUtil.setSharedPreferenceIntList(R.string.key_device_ids, deviceIds);

		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_mac, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_address, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_port, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_label, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_type, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_vendor, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_product, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_version, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_zone_count, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_tile_count, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_build_timestamp, deviceId);
		PreferenceUtil.removeIndexedSharedPreference(R.string.key_device_show, deviceId);

		for (StoredColor storedColor : ColorRegistry.getInstance().getStoredColors()) {
			if (storedColor.getDeviceId() == deviceId) {
				ColorRegistry.getInstance().remove(storedColor);
			}
		}
	}

	/**
	 * Remove all devices from local store.
	 */
	public void removeAll() {
		while (mDevices.size() > 0) {
			remove(mDevices.valueAt(mDevices.size() - 1));
		}
	}

	/**
	 * Get the DeviceRegistry as singleton.
	 *
	 * @return The DeviceRegistry as singleton.
	 */
	public static synchronized DeviceRegistry getInstance() {
		if (DeviceRegistry.mInstance == null) {
			DeviceRegistry.mInstance = new DeviceRegistry();
		}
		return DeviceRegistry.mInstance;
	}

	/**
	 * Update the list of devices.
	 *
	 * @param callback Callback called in case of found devices.
	 */
	public void update(final DeviceUpdateCallback callback) {
		new DeviceUpdateTask(this, callback).execute();
	}

	/**
	 * Enumeration of device types.
	 */
	public enum DeviceType {
		/**
		 * A device which is no light.
		 */
		DEVICE,
		/**
		 * A light which is neither multizone nor tilechain.
		 */
		LIGHT,
		/**
		 * A multizone light.
		 */
		MULTIZONE,
		/**
		 * A tile chain.
		 */
		TILECHAIN;

		/**
		 * Get the device type by its ordinal.
		 *
		 * @param i The ordinal
		 * @return The device type.
		 */
		public static DeviceType fromOrdinal(final int i) {
			for (DeviceType deviceType : DeviceType.values()) {
				if (deviceType.ordinal() == i) {
					return deviceType;
				}
			}
			return DEVICE;
		}

		/**
		 * Get the type of a device.
		 *
		 * @param device The device
		 * @return The type
		 */
		public static DeviceType fromDevice(final Device device) {
			if (device instanceof MultiZoneLight) {
				return MULTIZONE;
			}
			else if (device instanceof TileChain) {
				return TILECHAIN;
			}
			else if (device instanceof Light) {
				return LIGHT;
			}
			else {
				return DEVICE;
			}
		}

	}

	/**
	 * Asynchronous task for updating the list of devices.
	 */
	private static final class DeviceUpdateTask extends AsyncTask<String, Device, List<Device>> {
		/**
		 * The callback called for found devices.
		 */
		private final DeviceUpdateCallback mCallback;
		/**
		 * The MACs of new devices.
		 */
		private final List<String> mNewDeviceMacs = new ArrayList<>();
		/**
		 * The device registry.
		 */
		private final DeviceRegistry mDeviceRegistry;

		/**
		 * Create a DeviceUpdateTask.
		 *
		 * @param deviceRegistry The calling deviceRegistry.
		 * @param callback       The callback to be called for found devices.
		 */
		private DeviceUpdateTask(final DeviceRegistry deviceRegistry, final DeviceUpdateCallback callback) {
			mDeviceRegistry = deviceRegistry;
			mCallback = callback;
		}

		@Override
		protected List<Device> doInBackground(final String... params) {
			try {
				return LifxLan.getInstance().retrieveDeviceInformation(new RetryPolicy() {
					@Override
					public int getAttempts() {
						return 2;
					}

					@Override
					public int getTimeout(final int attempt) {
						return 2500; // MAGIC_NUMBER
					}

					@Override
					public int getExpectedResponses() {
						return Integer.MAX_VALUE;
					}

					@Override
					public void onResponse(final ResponseMessage responseMessage) {
						try {
							Device device = ((StateService) responseMessage).getDevice().getDeviceProduct();
							if (!mDeviceRegistry.mMacToIdMap.containsKey(device.getTargetAddress())) {
								mNewDeviceMacs.add(device.getTargetAddress());
							}
							mDeviceRegistry.addOrUpdate(device);
							publishProgress(device);
						}
						catch (IOException e) {
							Log.w(Application.TAG, e);
						}
					}
				}, null);
			}
			catch (IOException e) {
				Log.w(Application.TAG, e);
				return new ArrayList<>();
			}
		}

		@Override
		protected void onProgressUpdate(final Device... devices) {
			for (Device device : devices) {
				mCallback.onDeviceUpdated(device, mNewDeviceMacs.contains(device.getTargetAddress()), false);
			}
		}

		@Override
		protected void onPostExecute(final List<Device> devices) {
			List<String> foundMacs = new ArrayList<>();
			for (Device device : devices) {
				foundMacs.add(device.getTargetAddress());
			}
			for (String mac : mDeviceRegistry.mMacToIdMap.keySet()) {
				if (!foundMacs.contains(mac)) {
					mCallback.onDeviceUpdated(mDeviceRegistry.mDevices.get(mDeviceRegistry.mMacToIdMap.get(mac)), false, true);
				}
			}
			if (devices.size() == 0) {
				mCallback.onNoDevicesFound();
			}
		}
	}

	/**
	 * Callback for device search.
	 */
	public interface DeviceUpdateCallback {
		/**
		 * Method called on device search results.
		 *
		 * @param device    the device.
		 * @param isNew     true if the device is unknown.
		 * @param isMissing true if the device is known but was not found.
		 */
		void onDeviceUpdated(Device device, boolean isNew, boolean isMissing);

		/**
		 * Method called if no devices are found.
		 */
		default void onNoDevicesFound() {
			// do nothing.
		}
	}

}
