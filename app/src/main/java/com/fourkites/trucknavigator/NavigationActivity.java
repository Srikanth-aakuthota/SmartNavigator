package com.fourkites.trucknavigator;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.fourkites.trucknavigator.pojos.SelectedRoute;
import com.fourkites.trucknavigator.pojos.Stop;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.fabric.sdk.android.Fabric;

/**
 * Created by Srikanth on 04/05/21.
 */


public class NavigationActivity extends AppCompatActivity {

    private NavigationView navigationView;
    private TextToSpeech tts;
    private boolean isTtsEnabled = false;
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private final String TAG = "LOCATION MODE";
    private final String ACTIVITY_TAG = "lifecycle";
    private ArrayList<Stop> points;
    private SelectedRoute selectedRoute;
    private boolean directionsState;
    private boolean startState;
    private boolean stopState;
    private boolean toolbarState;
    private boolean navigationBarState;
    private boolean controlsState;
    private boolean schemeSwitchState;
    private boolean popUpLayoutState;
    private boolean mainViewState;
    private boolean routeViewState;
    private GpsInfoReceiver gpsInfoReceiver;
    private Button getStarted;
    private LinearLayout appLayout;
    private RelativeLayout splashLayout;
    //private Bundle savedState;
    private Bundle savedInstanceState;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        super.onCreate(savedInstanceState);
        Log.i(ACTIVITY_TAG, "onCreate: ");
        Fabric.with(this, new Crashlytics());
        gpsInfoReceiver = new GpsInfoReceiver();

        setContentView(R.layout.activity_main);

        appLayout = findViewById(R.id.appLayout);
        splashLayout = findViewById(R.id.splashLayout);
        getStarted = findViewById(R.id.start_button);

        //Restoring the data during Activity Restart
        if (savedInstanceState != null) {
            //savedState = savedInstanceState.getBundle("savedState");
            restoreDataWhenActivityRestarts(savedInstanceState);
        }

        // Initialize Text to Speech Engine
        initTTS();

        showHomeScreen();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

       /* // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }*/
    }

    private void showHomeScreen() {

        getStarted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                splashLayout.setVisibility(View.GONE);
                appLayout.setVisibility(View.VISIBLE);
                if (!Navigator.isMapLoaded) {
                    if (navigationView != null) {
                        navigationView.showProgressBasedOnMapAndPosition();
                    } else {
                        Log.e(TAG, "showHomeScreen onClick: Inialization error");
                    }
                }

            }
        });
    }


    private void startCountDown() {
        appLayout.setVisibility(View.GONE);
        splashLayout.setVisibility(View.VISIBLE);
        getStarted.setVisibility(View.GONE);
        new CountDownTimer(2000, 500) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                splashLayout.setVisibility(View.GONE);
                appLayout.setVisibility(View.VISIBLE);
            }
        }.start();
    }

    private void restoreDataWhenActivityRestarts(Bundle savedInstanceState) {
        points = savedInstanceState.getParcelableArrayList("waypoints");
        selectedRoute = savedInstanceState.getParcelable("route");
        directionsState = savedInstanceState.getBoolean("directionsState");
        startState = savedInstanceState.getBoolean("startState");
        stopState = savedInstanceState.getBoolean("stopState");
        toolbarState = savedInstanceState.getBoolean("toolbarState");
        navigationBarState = savedInstanceState.getBoolean("navigationBarState");
        controlsState = savedInstanceState.getBoolean("controlsState");
        schemeSwitchState = savedInstanceState.getBoolean("schemeSwitchState");
        popUpLayoutState = savedInstanceState.getBoolean("popUpLayoutState");
        mainViewState = savedInstanceState.getBoolean("mainViewState");
        routeViewState = savedInstanceState.getBoolean("routeViewState");

        if (mainViewState) {
            appLayout.setVisibility(View.VISIBLE);
            splashLayout.setVisibility(View.GONE);
        } else {
            appLayout.setVisibility(View.GONE);
            splashLayout.setVisibility(View.VISIBLE);
        }
    }


    private void initTTS() {
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {

                    int result = tts.setLanguage(Locale.US);

                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    } else {
                        isTtsEnabled = true;
                    }

                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
                requestPermissions();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(ACTIVITY_TAG, "onResume: ");

        LocalBroadcastManager.getInstance(this).registerReceiver(gpsInfoReceiver, new IntentFilter("GPS_INFO_UPDATE_ALERT"));
        if (navigationView != null && Navigator.navigationMode)
            navigationView.keepScreenOn();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gpsInfoReceiver);
        Log.i(ACTIVITY_TAG, "onPause: ");

        super.onPause();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (navigationView != null) {
            if (navigationView.getWaypoints() != null)
                outState.putParcelableArrayList("waypoints", navigationView.getWaypoints());
            if (navigationView.getSelectedRoute() != null)
                outState.putParcelable("route", navigationView.getSelectedRoute());
            if (navigationView.getCreateRoute() != null)
                outState.putBoolean("directionsState", navigationView.getCreateRoute().getVisibility() == View.VISIBLE);
            if (navigationView.getStart() != null)
                outState.putBoolean("startState", navigationView.getStart().getVisibility() == View.VISIBLE);
            if (navigationView.getStop() != null)
                outState.putBoolean("stopState", navigationView.getStop().getVisibility() == View.VISIBLE);
            if (navigationView.getToolbar() != null)
                outState.putBoolean("toolbarState", navigationView.getToolbar().getVisibility() == View.VISIBLE);
            if (navigationView.getNavigationBar() != null)
                outState.putBoolean("navigationBarState", navigationView.getNavigationBar().getVisibility() == View.VISIBLE);
            if (navigationView.getControls() != null)
                outState.putBoolean("controlsState", navigationView.getControls().getVisibility() == View.VISIBLE);
            if (navigationView.getSchemeSwitch() != null)
                outState.putBoolean("schemeSwitchState", navigationView.getSchemeSwitch().getVisibility() == View.VISIBLE);
            if (navigationView.getPopUpLayout() != null)
                outState.putBoolean("popUpLayoutState", navigationView.getPopUpLayout().getVisibility() == View.VISIBLE);
            if (appLayout != null)
                outState.putBoolean("mainViewState", appLayout.getVisibility() == View.VISIBLE);
            if (navigationView.getRouteDetailsLayout() != null)
                outState.putBoolean("routeViewState", navigationView.getRouteDetailsLayout().getVisibility() == View.VISIBLE);
            //outState.putBundle("savedState", savedState);
        }
    }


    /**
     * Only when the app's target SDK is 23 or higher, it requests each dangerous permissions it
     * needs when the app is running.
     */
    private void requestPermissions() {

        final List<String> requiredSDKPermissions = new ArrayList<String>();
        requiredSDKPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredSDKPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        requiredSDKPermissions.add(Manifest.permission.INTERNET);
        requiredSDKPermissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        requiredSDKPermissions.add(Manifest.permission.ACCESS_NETWORK_STATE);

        ActivityCompat.requestPermissions(this, requiredSDKPermissions.toArray(new String[requiredSDKPermissions.size()]), REQUEST_CODE_ASK_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS: {
                for (int index = 0; index < permissions.length; index++) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {

                        /**
                         * If the user turned down the permission request in the past and chose the
                         * Don't ask again option in the permission request system dialog.
                         */
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                                permissions[index])) {
                            Toast.makeText(this,
                                    "Required permission " + permissions[index] + " not granted. "
                                            + "Please go to settings and turn on for sample app",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    "Required permission " + permissions[index] + " not granted",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                displayLocationSettingsRequest(this);


                break;
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void displayLocationSettingsRequest(Context context) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000 / 2);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.i(TAG, "All location settings are satisfied.");
                        /**
                         * All permission requests are being handled.Create map fragment view.Please note
                         * the HERE SDK requires all permissions defined above to operate properly.
                         */
                        initMap();

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings ");

                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result
                            // in onActivityResult().
                            status.startResolutionForResult(NavigationActivity.this, 100);
                        } catch (IntentSender.SendIntentException e) {
                            Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        break;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 100: {
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        /**
                         * All permission requests are being handled.Create map fragment view.Please note
                         * the HERE SDK requires all permissions defined above to operate properly.
                         */
                        initMap();
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(NavigationActivity.this, "Please turn on the location for navigation", Toast.LENGTH_SHORT).show();
                        break;

                    default:
                        super.onActivityResult(requestCode, resultCode, data);
                        break;
                }
            }
        }
    }

    private void initMap() {
        if (points != null && points.size() > 0) {
            navigationView = new NavigationView(NavigationActivity.this, isTtsEnabled, tts, points, selectedRoute);
        } else {
            navigationView = new NavigationView(NavigationActivity.this, isTtsEnabled, tts);
        }
    }

    public Activity getActivityInstance() {
        return NavigationActivity.this;
    }

    @Override
    public void onBackPressed() {
        if (Navigator.navigationMode)
            showLogoutWarning(false, "Are you sure you want to exit the current navigation?", false);
        else
            showLogoutWarning(false, "Are you sure you want to exit the application?", true);
    }


    public void showLogoutWarning(final boolean get, String msg, final boolean allowExit) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder
                .setTitle("Confirm")
                .setMessage(msg)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        if (Navigator.navigationMode)
                            navigationView.stopNavigation(get, false);

                        if (dialog != null)
                            dialog.cancel();

                        if (allowExit)
                            finish();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (dialog != null)
                            dialog.cancel();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public boolean isDirectionsState() {
        return directionsState;
    }

    public boolean isStartState() {
        return startState;
    }

    public boolean isStopState() {
        return stopState;
    }

    public boolean isToolbarState() {
        return toolbarState;
    }

    public boolean isNavigationBarState() {
        return navigationBarState;
    }

    public boolean isControlsState() {
        return controlsState;
    }

    public boolean isSchemeSwitchState() {
        return schemeSwitchState;
    }

    public boolean isPopUpLayoutState() {
        return popUpLayoutState;
    }

    public boolean isRouteDetailsLayoutState() {
        return routeViewState;
    }

    /**
     * Update Ui regarding the Location Status
     */

    public class GpsInfoReceiver extends BroadcastReceiver {
        protected static final String TAG = "gps-receiver";

        @Override
        public void onReceive(Context context, Intent intent) {

            try {
                if (navigationView == null)
                    initMap();
                else if (intent.getBooleanExtra("isOn", false))
                    navigationView.getCurrentTrack();
                else
                    navigationView.showToast("Please turn on the location.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null)
            tts.shutdown();

        Log.i(ACTIVITY_TAG, "onDestroy: ");
        super.onDestroy();
    }
}
