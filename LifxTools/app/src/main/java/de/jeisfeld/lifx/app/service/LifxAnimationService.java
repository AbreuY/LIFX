package de.jeisfeld.lifx.app.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import de.jeisfeld.lifx.app.MainActivity;
import de.jeisfeld.lifx.app.R;
import de.jeisfeld.lifx.lan.LifxLan;
import de.jeisfeld.lifx.lan.Light;
import de.jeisfeld.lifx.lan.Light.AnimationCallback;
import de.jeisfeld.lifx.lan.MultiZoneLight;
import de.jeisfeld.lifx.lan.type.Color;
import de.jeisfeld.lifx.lan.type.MultizoneColors;

/**
 * A service handling LIFX animations in the background.
 */
public class LifxAnimationService extends Service {
	/**
	 * The id of the notification channel.
	 */
	public static final String CHANNEL_ID = "LifxAnimationChannel";
	/**
	 * Key for the notification text within the intent.
	 */
	public static final String EXTRA_NOTIFICATION_TEXT = "de.jeisfeld.lifx.NOTIFICATION_TEXT";
	/**
	 * Key for the device MAC within the intent.
	 */
	public static final String EXTRA_DEVICE_MAC = "de.jeisfeld.lifx.DEVICE_MAC";
	/**
	 * Key for the device Label within the intent.
	 */
	public static final String EXTRA_DEVICE_LABEL = "de.jeisfeld.lifx.DEVICE_LABEL";
	/**
	 * The intent of the broadcast for stopping an animation.
	 */
	public static final String EXTRA_ANIMATION_STOP_INTENT = "de.jeisfeld.lifx.ANIMATION_STOP_INTENT";
	/**
	 * Key for the broadcast data giving MAC of stopped animation.
	 */
	public static final String EXTRA_ANIMATION_STOP_MAC = "de.jeisfeld.lifx.ANIMATION_STOP_MAC";
	/**
	 * Map from MACs to Lights for all lights with running animations.
	 */
	private static final Map<String, Light> ANIMATED_LIGHTS = new HashMap<>();
	/**
	 * Map from MACs to Light labels for all lights with running animations.
	 */
	private static final Map<String, String> ANIMATED_LIGHT_LABELS = new HashMap<>();
	/**
	 * The local broadcast manager.
	 */
	private LocalBroadcastManager mBroadcastManager;

	@Override
	public final void onCreate() {
		super.onCreate();
		createNotificationChannel();
		mBroadcastManager = LocalBroadcastManager.getInstance(this);
	}

	@Override
	public final int onStartCommand(final Intent intent, final int flags, final int startId) {
		final String mac = intent.getStringExtra(EXTRA_DEVICE_MAC);
		final String label = intent.getStringExtra(EXTRA_DEVICE_LABEL);
		ANIMATED_LIGHT_LABELS.put(mac, label);

		startNotification();

		new Thread() {
			@Override
			public void run() {
				Light tmpLight;
				tmpLight = ANIMATED_LIGHTS.get(mac);
				if (tmpLight == null) {
					tmpLight = LifxLan.getInstance().getLightByMac(mac);
					synchronized (ANIMATED_LIGHTS) {
						ANIMATED_LIGHTS.put(mac, tmpLight);
					}
				}
				else {
					tmpLight.endAnimation(false);
				}

				if (tmpLight instanceof MultiZoneLight) {
					final MultiZoneLight light = (MultiZoneLight) tmpLight;
					light.rollingAnimation(10000, // MAGIC_NUMBER
							new MultizoneColors.Interpolated(true, Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE))
							.setBrightness(0.3) // MAGIC_NUMBER
							.setAnimationCallback(new AnimationCallback() {
								@Override
								public void onException(final IOException e) {
									updateOnEndAnimation(light);
								}

								@Override
								public void onAnimationEnd(final boolean isInterrupted) {
									updateOnEndAnimation(light);
								}
							})
							.start();
				}
				else {
					final Light light = tmpLight;
					light.wakeup(10000, new AnimationCallback() {
						@Override
						public void onException(final IOException e) {
							updateOnEndAnimation(light);
						}

						@Override
						public void onAnimationEnd(final boolean isInterrupted) {
							updateOnEndAnimation(light);
						}
					});
				}
			}
		}.start();

		return START_STICKY;
	}

	@Override
	public final void onDestroy() {
		super.onDestroy();
	}

	@Override
	public final IBinder onBind(final Intent intent) {
		return null;
	}

	/**
	 * Send broadcast informing about the end of an animation.
	 *
	 * @param mac The MAC for which the animation ended.
	 */
	public void sendBroadcastStopAnimation(final String mac) {
		Intent intent = new Intent(EXTRA_ANIMATION_STOP_INTENT);
		intent.putExtra(EXTRA_ANIMATION_STOP_MAC, mac);
		mBroadcastManager.sendBroadcast(intent);
	}

	/**
	 * Create the channel for service animation notifications.
	 */
	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel animationChannel = new NotificationChannel(
					CHANNEL_ID, getString(R.string.notification_channel_animation), NotificationManager.IMPORTANCE_DEFAULT);
			NotificationManager manager = getSystemService(NotificationManager.class);
			manager.createNotificationChannel(animationChannel);
		}
	}

	/**
	 * Start the notification.
	 */
	private void startNotification() {
		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this,
				0, notificationIntent, 0);
		Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle(getString(R.string.notification_title_animation))
				.setContentText(getString(R.string.notification_text_animation_running, getAnimatedDevicesString()))
				.setSmallIcon(R.drawable.ic_notification_icon_logo)
				.setContentIntent(pendingIntent)
				.build();
		startForeground(1, notification);
	}


	/**
	 * Stop the animation from UI for a given MAC.
	 *
	 * @param context the context.
	 * @param mac     The MAC
	 */
	public static void stopAnimationForMac(final Context context, final String mac) {
		Light light = ANIMATED_LIGHTS.get(mac);
		if (light != null) {
			light.endAnimation(false);
		}
	}

	/**
	 * Update the service after the animation has ended.
	 *
	 * @param light The light.
	 */
	private void updateOnEndAnimation(final Light light) {
		sendBroadcastStopAnimation(light.getTargetAddress());
		synchronized (ANIMATED_LIGHTS) {
			ANIMATED_LIGHTS.remove(light.getTargetAddress());
			ANIMATED_LIGHT_LABELS.remove(light.getTargetAddress());
			if (ANIMATED_LIGHTS.size() == 0) {
				Intent serviceIntent = new Intent(this, LifxAnimationService.class);
				stopService(serviceIntent);
			}
			else {
				startNotification();
			}
		}
	}

	/**
	 * Check if there is a running animation for a given MAC.
	 *
	 * @param mac the MAC.
	 * @return true if there is a running animation for this MAC.
	 */
	public static boolean hasRunningAnimation(final String mac) {
		return ANIMATED_LIGHTS.containsKey(mac);
	}

	/**
	 * Get a display String for all animated devices.
	 *
	 * @return a display String for all animated devices.
	 */
	public static String getAnimatedDevicesString() {
		StringBuilder builder = new StringBuilder();
		for (String label : ANIMATED_LIGHT_LABELS.values()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(label);
		}
		return builder.toString();
	}

}
