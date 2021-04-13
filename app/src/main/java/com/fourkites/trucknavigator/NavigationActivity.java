package com.fourkites.trucknavigator;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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

    /**
     * UI Element
     */
    private FloatingActionButton searchForNewDevices;
    private ListView DevicesList;

    /**
     * Return Intent extra (The device MAC address)
     */
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    /**
     * The adapter to get all bluetooth services
     */
    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices;

    /**
     * the MAC address for the chosen device
     */
    String address = null;

    private ProgressDialog progressDialog;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    //SPP UUID. Look for it'
    //This the SPP for the arduino(AVR)
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private int newConnectionFlag = 0;
    /**
     * request to enable bluetooth form activity result
     */
    public static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_ENABLE_FINE_LOCATION = 1256;

    /**
     * Adapter for the devices list
     */
    private BluetoothDevicesAdapter bluetoothDevicesAdapter;
    private ArrayList<String> bluetoothDevicesNamesList = new ArrayList<String>();

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
        initializeBlueTooth();

        //check if the device has a bluetooth or not
        //and show Toast message if it does't have
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevicesAdapter = new BluetoothDevicesAdapter(this, bluetoothDevicesNamesList);

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.does_not_have_bluetooth, Toast.LENGTH_LONG).show();
            finish();
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntentBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntentBluetooth, REQUEST_ENABLE_BT);
        } else if (mBluetoothAdapter.isEnabled()) {
            PairedDevicesList();
        }

       setBroadCastReceiver ();
        //press the button to start search new Devices
        searchForNewDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchForNewDevices.setEnabled(false);
                bluetoothDevicesAdapter.clear();
                PairedDevicesList();
                NewDevicesList();
            }
        });

     //Restoring the data during Activity Restart
        if (savedInstanceState != null) {
            //savedState = savedInstanceState.getBundle("savedState");
            restoreDataWhenActivityRestarts(savedInstanceState);
        }

        // Initialize Text to Speech Engine
        initTTS();
        showHomeScreen();
    }


    /**
     * Link the layout element from XML to Java
     */
    private void initializeBlueTooth() {
        searchForNewDevices = (FloatingActionButton) findViewById(R.id.search_fab_button);
        DevicesList = (ListView) findViewById(R.id.devices_list_listView);
    }


    private void blueToothSearch () {

        bluetoothDevicesAdapter.clear ();
        PairedDevicesList ();
        NewDevicesList ();

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
                    Log.e("TTS", "Initialization Failed!");
                }
                requestPermissions();
            }
        });
    }

    /**
     * fast way to call Toast
     */
    private void makeToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * to disconnect the bluetooth connection
     */
    private void Disconnect() {
        if (btSocket != null) //If the btSocket is busy
        {
            try {
                btSocket.close(); //close connection
            } catch (IOException e) {
                makeToast("Error");
            }
        }
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
    protected void onStop() {
        super.onStop();
        Disconnect();
        finish();
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
        requiredSDKPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredSDKPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        requiredSDKPermissions.add(Manifest.permission.BLUETOOTH);
        ActivityCompat.requestPermissions(this, requiredSDKPermissions.toArray(new String[requiredSDKPermissions.size()]), REQUEST_CODE_ASK_PERMISSIONS);
    }
    /**
     * to set the BroadCaster Receiver
     */
    private void setBroadCastReceiver() {
        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);


        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);
    }

    /**
     * scan for new Devices and pair with them
     */
    private void NewDevicesList() {
        // If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBluetoothAdapter.startDiscovery();
    }

    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {

                    bluetoothDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                searchForNewDevices.setEnabled(true);
            }
        }
    };

    /**
     * get the paired devices in the phone
     */
    private void PairedDevicesList() {
        pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                //Get the device's name and the address
                bluetoothDevicesAdapter.add(bt.getName() + "\n" + bt.getAddress());
            }
        } else {
            Toast.makeText(getApplicationContext(), R.string.no_paired_devices,
                    Toast.LENGTH_LONG).show();
        }

        DevicesList.setAdapter(bluetoothDevicesAdapter);
        DevicesList.setOnItemClickListener(bluetoothListClickListener);
    }

    /**
     * handle the click for the list view to get the MAC address
     */
    private AdapterView.OnItemClickListener bluetoothListClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            //Get the device MAC address , the last 17 char in the view
            String info = (String) parent.getItemAtPosition(position);
            String MACAddress = info.substring(info.length() - 17);
            address= MACAddress;
            
			newConnectionFlag++;
        
		if (address != null) {
            //call the class to connect to bluetooth
            if (newConnectionFlag == 1) {
                new ConnectBT().execute();
            }
        }

            //finish();
        }
    };

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
            case REQUEST_ENABLE_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permission granted!
                } else {
                    Toast.makeText(this, "Access Location must be allowed for bluetooth Search", Toast.LENGTH_LONG).show();
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
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                PairedDevicesList();
            } else {
                finish();
            }
        }   
		
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

    /**
     * An AysncTask to connect to Bluetooth socket
     */
    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean connectSuccess = true;

        @Override
        protected void onPreExecute() {

            //show a progress dialog
            progressDialog = ProgressDialog.show(NavigationActivity.this,
                    "Connecting...", "Please wait!!!");
        }

        //while the progress dialog is shown, the connection is done in background
        @Override
        protected Void doInBackground(Void... params) {

            try {
                if (btSocket == null || !isBtConnected) {
                    //get the mobile bluetooth device
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                    //connects to the device's address and checks if it's available
                    BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);

                    //create a RFCOMM (SPP) connection
                    btSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(myUUID);

                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

                    //start connection
                    btSocket.connect();
                }

            } catch (IOException e) {
                //if the try failed, you can check the exception here
                connectSuccess = false;
            }

            return null;
        }

        //after the doInBackground, it checks if everything went fine
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.e("Testing", connectSuccess + "");
            if (!connectSuccess) {
                makeToast("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                isBtConnected = true;
                makeToast("Connected");
            }
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null)
            tts.shutdown();

        Log.i(ACTIVITY_TAG, "onDestroy: ");
        // Make sure we're not doing discovery anymore
       if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        } 

        // Unregister broadcast listeners
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
}
