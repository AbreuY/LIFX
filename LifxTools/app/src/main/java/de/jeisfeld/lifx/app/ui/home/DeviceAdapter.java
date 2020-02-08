package de.jeisfeld.lifx.app.ui.home;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import de.jeisfeld.lifx.R;
import de.jeisfeld.lifx.app.ui.home.HomeFragment.NoDeviceCallback;
import de.jeisfeld.lifx.app.util.DeviceRegistry;
import de.jeisfeld.lifx.app.util.DeviceRegistry.DeviceUpdateCallback;
import de.jeisfeld.lifx.lan.Device;
import de.jeisfeld.lifx.lan.Light;
import de.jeisfeld.lifx.lan.MultiZoneLight;
import de.jeisfeld.lifx.lan.type.Power;

/**
 * An adapter for the list of devices in the home fragment.
 */
class DeviceAdapter extends BaseAdapter {
	/**
	 * The list of devices.
	 */
	private final List<Device> mDevices;
	/**
	 * The list of view models.
	 */
	private final List<DeviceViewModel> mViewModels = new ArrayList<>();

	/**
	 * The context.
	 */
	private final Context mContext;
	/**
	 * The lifecycle owner.
	 */
	private final LifecycleOwner mLifeCycleOwner;
	/**
	 * Callback on change of "no device" status.
	 */
	private final NoDeviceCallback mNoDeviceCallback;
	/**
	 * A store for the views.
	 */
	private final List<View> mViews = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param fragment The triggering fragment.
	 * @param callback A calllback called in case of no devices.
	 */
	DeviceAdapter(final Fragment fragment, final NoDeviceCallback callback) {
		super();
		mDevices = DeviceRegistry.getInstance().getDevices();
		mContext = fragment.getContext();
		mLifeCycleOwner = fragment.getViewLifecycleOwner();
		mNoDeviceCallback = callback;
		for (Device device : mDevices) {
			if (device instanceof MultiZoneLight) {
				mViewModels.add(new MultizoneViewModel((MultiZoneLight) device));
			}
			else if (device instanceof Light) {
				mViewModels.add(new LightViewModel((Light) device));
			}
			else {
				mViewModels.add(new DeviceViewModel(device));
			}
		}

		DeviceRegistry.getInstance().update(new DeviceUpdateCallback() {
			@Override
			public void onDeviceUpdated(final Device device, final boolean isNew, final boolean isMissing) {
				if (isNew) {
					if (mDevices.size() == 0) {
						mNoDeviceCallback.onChange(true);
					}
					addDevice(device);
				}
			}

			@Override
			public void onNoDevicesFound() {
				if (mDevices.size() == 0) {
					mNoDeviceCallback.onChange(false);
				}
			}
		});
	}

	@Override
	public int getCount() {
		return mDevices.size();
	}

	@Override
	public Device getItem(final int position) {
		return mDevices.get(position);
	}

	@Override
	public long getItemId(final int position) {
		return position;
	}

	/**
	 * Refresh view data for all devices.
	 */
	protected void refresh() {
		for (DeviceViewModel model : mViewModels) {
			model.refresh();
		}
	}

	@Override
	public synchronized View getView(final int position, final View convertView, final ViewGroup parent) {
		final View view;

		// do not use convertView, as information is stored in the views
		synchronized (mViews) {
			if (mViews.size() > position && mViews.get(position) != null) {
				return mViews.get(position);
			}
		}

		final Device device = getItem(position);

		int layoutId = R.layout.list_view_home_device;
		if (device instanceof MultiZoneLight) {
			layoutId = R.layout.list_view_home_multizone;
		}
		else if (device instanceof Light) {
			layoutId = R.layout.list_view_home_light;
		}

		view = LayoutInflater.from(mContext).inflate(layoutId, parent, false);

		final TextView text = view.findViewById(R.id.textViewHome);
		text.setText(device.getLabel());

		DeviceViewModel model = mViewModels.get(position);

		preparePowerButton(view.findViewById(R.id.buttonPower), model);

		synchronized (mViews) {
			if (mViews.size() > position) {
				mViews.set(position, view);
			}
			else {
				mViews.add(position, view);
			}
		}

		model.checkPower();

		if (device instanceof MultiZoneLight) {
			prepareToggleButton(view.findViewById(R.id.toggleButtonThread), (MultizoneViewModel) model);
		}

		return view;
	}

	/**
	 * Prepare the power button.
	 *
	 * @param powerButton The power button.
	 * @param model The device view model.
	 */
	private void preparePowerButton(final Button powerButton, final DeviceViewModel model) {
		model.getPower().observe(mLifeCycleOwner, new Observer<Power>() {
			@Override
			public void onChanged(final Power power) {
				if (power == null) {
					powerButton.setBackground(mContext.getDrawable(R.drawable.powerbutton_offline));
				}
				else if (power.isOn()) {
					powerButton.setBackground(mContext.getDrawable(R.drawable.powerbutton_on));
				}
				else if (power.isOff()) {
					powerButton.setBackground(mContext.getDrawable(R.drawable.powerbutton_off));
				}
				else {
					powerButton.setBackground(mContext.getDrawable(R.drawable.powerbutton_undefined));
				}
			}
		});

		powerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				model.togglePower();
			}
		});
	}

	/**
	 * Prepare the power button.
	 *
	 * @param toggleButton The toggle button.
	 * @param model The multizone device view model.
	 */
	private void prepareToggleButton(final ToggleButton toggleButton, final MultizoneViewModel model) {
		model.getAnimationStatus().observe(mLifeCycleOwner, new Observer<Boolean>() {
			@Override
			public void onChanged(final Boolean animationStatus) {
				toggleButton.setChecked(animationStatus);
			}
		});

		toggleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				model.updateAnimation(((ToggleButton) v).isChecked());
			}
		});
	}

	/**
	 * Add a device to the list.
	 *
	 * @param device The device to be added.
	 */
	public void addDevice(final Device device) {
		mDevices.add(device);
		mViewModels.add(new DeviceViewModel(device));
		notifyDataSetChanged();
	}
}