package de.jeisfeld.lifx.app;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.LocaleList;
import android.util.Log;
import de.jeisfeld.lifx.app.util.PreferenceUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utility class to retrieve base application resources.
 */
@SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS",
		justification = "Intentionally using same name as superclass")
public class Application extends android.app.Application {
	/**
	 * A utility field to store a context statically.
	 */
	private static Context mContext;
	/**
	 * The default tag for logging.
	 */
	public static final String TAG = "LIFX.JE";
	/**
	 * The default locale.
	 */
	private static final Locale DEFAULT_LOCALE = Locale.getDefault();

	@Override
	@SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
			justification = "Make some context visible statically (no matter which one)")
	public final void onCreate() {
		super.onCreate();
		Application.mContext = getApplicationContext();
		Application.mContext = Application.createContextWrapperForLocale(getApplicationContext());

		setExceptionHandler();

		// Set statistics
		int initialVersion = PreferenceUtil.getSharedPreferenceInt(R.string.key_statistics_initialversion, -1);
		if (initialVersion == -1) {
			PreferenceUtil.setSharedPreferenceInt(R.string.key_statistics_initialversion, Application.getVersion());
		}

		long firstStartTime = PreferenceUtil.getSharedPreferenceLong(R.string.key_statistics_firststarttime, -1);
		if (firstStartTime == -1) {
			PreferenceUtil.setSharedPreferenceLong(R.string.key_statistics_firststarttime, System.currentTimeMillis());
		}

		PreferenceUtil.incrementCounter(R.string.key_statistics_countstarts);
	}

	/**
	 * Define custom ExceptionHandler which ensures that notification alarms are not lost in case of error.
	 */
	private void setExceptionHandler() {
		final UncaughtExceptionHandler defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

		UncaughtExceptionHandler customExceptionHandler =
				(thread, ex) -> {
					// re-throw critical exception further to the os
					defaultExceptionHandler.uncaughtException(thread, ex);
				};

		Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler);
	}

	/**
	 * Retrieve the application context.
	 *
	 * @return The (statically stored) application context
	 */
	public static Context getAppContext() {
		return Application.mContext;
	}

	/**
	 * Get a resource string.
	 *
	 * @param resourceId the id of the resource.
	 * @param args arguments for the formatting
	 * @return the value of the String resource.
	 */
	public static String getResourceString(final int resourceId, final Object... args) {
		return Application.getAppContext().getResources().getString(resourceId, args);
	}

	/**
	 * Retrieve the version number of the app.
	 *
	 * @return the app version.
	 */
	@SuppressWarnings("deprecation")
	public static int getVersion() {
		PackageInfo pInfo;
		try {
			pInfo = Application.getAppContext().getPackageManager().getPackageInfo(Application.getAppContext().getPackageName(), 0);
			return pInfo.versionCode;
		}
		catch (NameNotFoundException e) {
			Log.e(Application.TAG, "Did not find application version", e);
			return 0;
		}
	}

	/**
	 * Retrieve the version String of the app.
	 *
	 * @return the app version String.
	 */
	public static String getVersionString() {
		PackageInfo pInfo;
		try {
			pInfo = Application.getAppContext().getPackageManager().getPackageInfo(Application.getAppContext().getPackageName(), 0);
			return pInfo.versionName;
		}
		catch (NameNotFoundException e) {
			Log.e(Application.TAG, "Did not find application version", e);
			return null;
		}
	}

	/**
	 * Get the configured application locale.
	 *
	 * @return The configured application locale.
	 */
	private static Locale getApplicationLocale() {
		String languageString = PreferenceUtil.getSharedPreferenceString(R.string.key_pref_language);
		if (languageString == null || languageString.length() == 0) {
			languageString = "0";
			PreferenceUtil.setSharedPreferenceString(R.string.key_pref_language, "0");
		}

		int languageSetting = Integer.parseInt(languageString);
		switch (languageSetting) {
		case 0:
			return Application.DEFAULT_LOCALE;
		case 1:
			return Locale.ENGLISH;
		case 2:
			return Locale.GERMAN;
		case 3: // MAGIC_NUMBER
			return new Locale("es");
		default:
			return Application.DEFAULT_LOCALE;
		}
	}

	/**
	 * Create a ContextWrapper, wrappint the context with a specific locale.
	 *
	 * @param context The original context.
	 * @return The context wrapper.
	 */
	@SuppressWarnings("deprecation")
	public static ContextWrapper createContextWrapperForLocale(final Context context) {
		Resources res = context.getResources();
		Configuration configuration = res.getConfiguration();
		Locale newLocale = Application.getApplicationLocale();
		Context newContext = context;

		if (VERSION.SDK_INT >= VERSION_CODES.N) {
			configuration.setLocale(newLocale);

			LocaleList localeList = new LocaleList(newLocale);
			LocaleList.setDefault(localeList);
			configuration.setLocales(localeList);

			newContext = context.createConfigurationContext(configuration);

		}
		else if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
			configuration.setLocale(newLocale);
			newContext = context.createConfigurationContext(configuration);

		}
		else {
			configuration.locale = newLocale;
			res.updateConfiguration(configuration, res.getDisplayMetrics());
		}
		return new ContextWrapper(newContext);
	}

	/**
	 * Start the app programmatically.
	 *
	 * @param triggeringActivity triggeringActivity the triggering activity.
	 */
	public static void startApplication(final Activity triggeringActivity) {
		Intent intent = new Intent(triggeringActivity, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		triggeringActivity.startActivity(intent);
	}
}
