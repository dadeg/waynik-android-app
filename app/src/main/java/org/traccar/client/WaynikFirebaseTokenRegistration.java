package org.traccar.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Created by dan on 5/27/2017.
 */

public class WaynikFirebaseTokenRegistration {
    private static final String TAG = "WaynikFirebaseRegSrv";
    /**
     * Persist token to third-party servers.
     *
     * Modify this method to associate the user's FCM InstanceID token with any server-side account
     * maintained by your application.
     *
     * @param token The new token.
     */
    public void sendRegistrationToServer(Context context, String token) {
        // TODO: Implement this method to send token to your app server.
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String email = preferences.getString(MainActivity.KEY_EMAIL, null);
        String apiToken = preferences.getString(MainActivity.KEY_TOKEN, null);
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https").encodedAuthority("prgd6brc2f.execute-api.us-west-2.amazonaws.com" + ':' + 443)
                .appendPath("production")
                .appendPath("notifications")
                .appendPath("register")
                .appendQueryParameter("security_token", apiToken)
                .appendQueryParameter("email", email)
                .appendQueryParameter("device_token", token);
        Log.d(TAG, builder.build().toString());
        RequestManager.sendRequestAsync(builder.build().toString(), new RequestManager.RequestHandler() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    Log.d(TAG, "successfully sent token to server");
                } else {
                    Log.d(TAG, "failed to send token to server");
                }

            }
        });
    }
}
