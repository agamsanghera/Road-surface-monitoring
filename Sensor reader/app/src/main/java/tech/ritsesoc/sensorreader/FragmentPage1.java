/*
 * FragmentPage1: Homepage
 * For starting the recording session
 */

package tech.ritsesoc.sensorreader;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.chip.Chip;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class FragmentPage1 extends Fragment implements SensorEventListener {
    private static final int REQUEST_CODE_STORAGE = 300;
    private static final int REQUEST_CODE = 200;
    //private TextView textView19;
    private long startTime, timestamp;
    private long now = 0;
    private int freq = 0;

    public ReaderService gpsService;
    /** Defines callbacks for service binding, passed to bindService() */
    public ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ReaderService.LocalBinder binder = (ReaderService.LocalBinder) service;
            gpsService = binder.getService();
            //mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            //mBound = false;
        }
    };

    private double accX = 0, accY = 0, accZ = 0,
            gyrX = 0, gyrY = 0, gyrZ = 0,
//            magX = 0, magY = 0, magZ = 0,
//            magA = 0, magP = 0, magR = 0,
//            graX=0, graY=0, graZ=0,
            speed = 0;

    private EditText editTextAccX, editTextAccY, editTextAccZ;
    private EditText editTextGyrX, editTextGyrY, editTextGyrZ;
//    private EditText editTextMagX, editTextMagY, editTextMagZ, editTextMagA, editTextMagP, editTextMagR;
//    private EditText editTextGraX, editTextGraY, editTextGraZ;
    private EditText editTextLat, editTextLong, editTextSpeed;
//    private EditText editTextSampleRate;
    private EditText editTextFileName;

    private SensorManager sensorManager;
    private Sensor sensorAcc, sensorGyr, sensorMag, sensorGra;
    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];
    private LocationManager locationManager;
    private Button buttonStart;
    private Button buttonUploadActivity;
    private double latitude, longitude;
    private int sampleRate;

    private File newFile;
    private BufferedWriter bw;
    private FileWriter fw;

    private Handler mHandler;
    private Runnable mRunnable;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Chip chipBicycle, chipMotorcycle, chipCar;

    private TextView textViewRecording;
    private TextView textViewTime;


    // A method that runs thread and makes it retrieve sensor values every time unit.
    // Called when the start button is pressed.
    public void useHandler() {
        freq = 0;
        startTime = System.currentTimeMillis();
        mHandler = new Handler();
        mHandler.post(mRunnable);//.postDelayed(mRunnable, 1000);
    }

    // requestPermissionLauncher and checkPermissions():
    // Perform Runtime Permission checks and requests
    // Returns boolean true if successful.
    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
        Log.d("Permissions", "Launcher result: " + isGranted.toString());
        if (isGranted.containsValue(false)) {
            checkPermissions();
        }
    });
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        Log.d("Permissions", "Launcher result: " + isGranted.toString());
        if (!isGranted) {
            checkPermissions();
        }
    });
    public boolean checkPermissions(){
        // Runtime Permissions are not needed before Android Marshmallow, only install-time permissions.
        // If below Marshmallow, cancel the request.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        AlertDialog.Builder ad1 = new AlertDialog.Builder(getContext());
        AlertDialog.Builder ad2 = new AlertDialog.Builder(getContext());
        AlertDialog alertDialog1;
        final AlertDialog[] alertDialog2 = new AlertDialog[1];
        ad1.setIcon(android.R.drawable.ic_dialog_alert);
        ad1.setTitle(R.string.warning_title);
        ad2.setIcon(android.R.drawable.ic_dialog_alert);
        ad2.setTitle(R.string.warning_title);

        ArrayList<String> requestPermissions = new ArrayList<String>();

        /* LOCATION PERMISSIONS */
        // FINE location must be asked together with COARSE location
        if (requireActivity().checkSelfPermission(ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.add(ACCESS_COARSE_LOCATION);
        }
        if (requireActivity().checkSelfPermission(ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.add(ACCESS_FINE_LOCATION);
        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            if (requireActivity().checkSelfPermission(ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED){
//                requestPermissions.add(ACCESS_BACKGROUND_LOCATION);
//            }
//        }

        // STORAGE PERMISSIONS
        // READ_EXTERNAL_STORAGE for Android 9 (API 28) or lower
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if(getActivity().checkSelfPermission(READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermissions.add(READ_EXTERNAL_STORAGE);
            }
        }
        // WRITE_EXTERNAL_STORAGE for versions lower than Android 11 (API 30)
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if(getActivity().checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermissions.add(WRITE_EXTERNAL_STORAGE);
            }
        }

        // Launch permission request
        // Permissions must be requested all at once, so we pass the ArrayList of Permissions to
        // requestPermissionLauncher.launch()
        if (requestPermissions.size() > 0) {
            ad1.setMessage(R.string.warning_location_storage_permission);
            ad1.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String[] location_reqperm = requestPermissions.toArray(new String[requestPermissions.size()]);
                    requestMultiplePermissionsLauncher.launch(location_reqperm);

                    // BACKGROUND location is only needed for Android 10 (APK 29) or higher
                    // Needs to be asked specifically after COARSE and FINE (foreground) location permissions
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (requireActivity().checkSelfPermission(ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED){
                            ad2.setMessage(R.string.warning_location_background_permission);
                            ad2.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    requestPermissionLauncher.launch(ACCESS_BACKGROUND_LOCATION);
                                }
                            });
                        }

                        alertDialog2[0] = ad2.create();
                        alertDialog2[0].show();
                    }
                }
            });
            alertDialog1 = ad1.create();
            alertDialog1.show();


        } else {
            return true;
        }

        // fallback if everything else fails
        return false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_page1, container, false);

        PowerManager powerManager = (PowerManager) requireContext().getSystemService(
                requireContext().POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK|PowerManager.LOCATION_MODE_NO_CHANGE,
                "sensorreader:wakeLockFragment1");
        wakeLock.setReferenceCounted(false);

        // TODO: Doesn't seem to be used?
//        final Boolean[] status_read = new Boolean[1];
//        status_read[0] = false;

        //editTextSampleRate = (EditText) findViewById(R.id.editTextSampleRate);
        editTextFileName = v.findViewById(R.id.editTextFileName);
        editTextAccX = v.findViewById(R.id.editTextAccX);
        editTextAccY = v.findViewById(R.id.editTextAccY);
        editTextAccZ = v.findViewById(R.id.editTextAccZ);
        editTextGyrX = v.findViewById(R.id.editTextGyrX);
        editTextGyrY = v.findViewById(R.id.editTextGyrY);
        editTextGyrZ = v.findViewById(R.id.editTextGyrZ);
        editTextLat = v.findViewById(R.id.editTextLat);
        editTextLong = v.findViewById(R.id.editTextLon);

        buttonStart = v.findViewById(R.id.buttonStart);
        buttonUploadActivity = v.findViewById(R.id.buttonUploadActivity);

        chipBicycle = v.findViewById(R.id.chipBicycle);
        chipMotorcycle = v.findViewById(R.id.chipMotorcycle);
        chipCar = v.findViewById(R.id.chipCar);

        textViewRecording = v.findViewById(R.id.textViewRecording);
        textViewTime = v.findViewById(R.id.textViewTime);

        //sensor manager
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorGyr = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorGra = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        //location manager
        locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);

        //PowerManager powerManager = (PowerManager) getContext().getSystemService(getContext().POWER_SERVICE);
        //final PowerManager.WakeLock  wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK |
        //        PowerManager.ACQUIRE_CAUSES_WAKEUP |
        //       PowerManager.ON_AFTER_RELEASE, "appname::WakeLock");

        //Intent intent = new Intent(getContext().getApplicationContext(), ReaderService.class);
        //requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Makes the thread retrieve sensor values every time unit.
        mRunnable = new Runnable() {
            @Override
            public void run() {
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        Calendar c = Calendar.getInstance();

                        //c.setTimeInMillis(System.currentTimeMillis());
                        SimpleDateFormat format1 = new SimpleDateFormat("yyMMdd'T'hhmmssSSS");
                        String date = format1.format(c.getTime());//+"-"+c.get(Calendar.HOUR_OF_DAY)+":"+c.get(Calendar.MINUTE)+":"+c.get(Calendar.SECOND)+":"+c.get(Calendar.MILLISECOND);

                        now = System.currentTimeMillis();
                        //Date date = new Date(now);
                        long time = now - timestamp;

                        String record = accX + "," + accY + "," + accZ + ","
                                + gyrX + "," + gyrY + "," + gyrZ + ","
                                /*
                                + magX + "," + magY + "," + magZ + ","
                                + magA + "," + magP + "," + magR + ","
                                + graX + "," + graY + "," + graZ + ","
                                */
                                + latitude + "," + longitude + ","
                                + speed + "," + time + "," + date + "\n";

                        //String record = accX + "," + accY + "," + accZ + "," + gyrX + "," + gyrY + "," + gyrZ + "," + magX + "," + magY + "," + magZ + "," + magA + "," + magP + "," + magR + "," + graX + "," + graY + "," + graZ + "," + latitude + "," + longitude + "," + speed + "," + date + "\n";
                        //String record = accX + "," + accY + "," + accZ + "," + gyrX + "," + gyrY + "," + gyrZ + "," + magX + "," + magY + "," + magZ + "," + magA + "," + magP + "," + magR + "," + graX + "," + graY + "," + graZ + "," + gpsService.getLatitude() + "," + gpsService.getLongitude() + "," + gpsService.getSpeed() + "," + time + "," + date + "\n";
                        //System.out.println(record);

                        try {
                            bw.write(record);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }

                        // update every second
                        if (time % 500 < 50) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(time),
                                            TimeUnit.MILLISECONDS.toMinutes(time) % TimeUnit.HOURS.toMinutes(1),
                                            TimeUnit.MILLISECONDS.toSeconds(time) % TimeUnit.MINUTES.toSeconds(1));
                                    textViewTime.setText(hms);

                                    editTextAccX.setText(String.valueOf((double)Math.round(accX*10000)/10000));
                                    editTextAccY.setText(String.valueOf((double)Math.round(accY*10000)/10000));
                                    editTextAccZ.setText(String.valueOf((double)Math.round(accZ*10000)/10000));
                                    editTextGyrX.setText(String.valueOf((double)Math.round(gyrX*10000)/10000));
                                    editTextGyrY.setText(String.valueOf((double)Math.round(gyrY*10000)/10000));
                                    editTextGyrZ.setText(String.valueOf((double)Math.round(gyrZ*10000)/10000));
                                    editTextLat.setText(String.valueOf(latitude));
                                    editTextLong.setText(String.valueOf(longitude));
                                }
                            });
                        }

                        mHandler.postDelayed(mRunnable, sampleRate);
                        /*
                        if (isServiceRunningInForeground(getContext(),ReaderService.class)){
                            Log.e("LOG STATUS:","Forground");
                        }
                        else{
                            Log.e("LOG STATUS:","Background");
                        }
                        */

                        /*
                        // turns on phone every 30 seconds
                        if ((now - startTime) > 30000){

                            // checks foreground application
                            if (isAppOnForeground(getContext(), "tech.ritsesoc.sensorreader")){
                                Toast.makeText(requireActivity(),"FOREGROUND !!",Toast.LENGTH_LONG).show();
                                Log.e("LOG STATUS","Foreground");
                            }
                            else{
                                Toast.makeText(requireActivity(),"BACK !!",Toast.LENGTH_LONG).show();
                                Log.e("LOG STATUS","Background");
                            }

                            if (wakeLock.isHeld() == false) {
                                wakeLock.acquire();

                            }
                        }
                        if ((now - startTime) > 40000){
                            if (wakeLock.isHeld()){
                                wakeLock.release();
                            }
                            startTime = now;
                        }
                        */



                        // records the number of samples taken every second.
                        //if (now <= (startTime + 30000)) {
                        //    freq++;
                        //} else {
                        //update tampilan frequency
                        //textView19.setText(String.valueOf(freq) + " Hz");
                        //    freq = 0;
                        //    startTime = now;

                            /*/ displays sensor values every 1 second.
                            editTextAccX.setText(String.valueOf(accX));
                            editTextAccY.setText(String.valueOf(accY));
                            editTextAccZ.setText(String.valueOf(accZ));

                            editTextGyrX.setText(String.valueOf(gyrX));
                            editTextGyrY.setText(String.valueOf(gyrY));
                            editTextGyrZ.setText(String.valueOf(gyrZ));

                            editTextMagX.setText(String.valueOf(magX));
                            editTextMagY.setText(String.valueOf(magY));
                            editTextMagZ.setText(String.valueOf(magZ));
                            editTextMagA.setText(String.valueOf(magA));
                            editTextMagP.setText(String.valueOf(magP));
                            editTextMagR.setText(String.valueOf(magR));

                            editTextGraX.setText(String.valueOf(graX));
                            editTextGraY.setText(String.valueOf(graY));
                            editTextGraZ.setText(String.valueOf(graZ));

                            editTextLat.setText(String.valueOf(latitude));
                            editTextLong.setText(String.valueOf(longitude));
                            editTextSpeed.setText(String.valueOf(speed));

                             */

                        //just for test (stop in 1 second)
                        //buttonStart.setText("START");
                        //mHandler.removeCallbacks(mRunnable);

                        //acquire will turn on the display
                        //wakeLock.acquire();

                        //release will release the lock from CPU, in case of that, screen will go back to sleep mode in defined time bt device settings
                        //wakeLock.release();

                        //}
                    }
                });
            }
        };

        buttonStart.setOnClickListener(new View.OnClickListener() {

            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View v) {
                // Check and gain location permissions
                boolean permission = checkPermissions();
                boolean gpsStat = checkGPSStatus();
                if (permission && gpsStat) {
                    buildLocationRequest();
                    builtLocationCallback();

                    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());

                    //now = System.currentTimeMillis();
                    //Date currentTime = Calendar.getInstance().getTime();
                    //String fileName = currentTime.toString();
                    //fileName = fileName.replace("+", "");
                    //fileName = fileName.replace(":", "");
                    //fileName = fileName.replace(" ", "");

                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat format1 = new SimpleDateFormat("yyMMdd'T'hhmmssSSS");
                    String date = format1.format(c.getTime());
                    String fileName = "";
                    if (chipBicycle.isChecked()) {
                        fileName = "sep";
                    } else if (chipMotorcycle.isChecked()) {
                        fileName = "mot";
                    } else
                        fileName = "mob";
                    fileName += "_" + Build.MANUFACTURER + "_" + Build.MODEL + "_" + date;
                    if (editTextFileName.getText().length() != 0)
                        fileName += "_"+editTextFileName.getText();

                    //editTextFileName.setText(fileName)

                    // compares the current buttonStart's label to "START"
                    if (buttonStart.getText().toString().equals(
                            getResources().getString(R.string.button_start))) {
                        startTheService();

                        textViewRecording.setText(R.string.text_recording);
                        buttonStart.setBackgroundColor(Color.RED);
                        chipMotorcycle.setCheckable(false);
                        chipBicycle.setCheckable(false);
                        chipCar.setCheckable(false);
                        buttonUploadActivity.setEnabled(false);

                        // Initialization
                        freq = 0;
                        timestamp = System.currentTimeMillis();

                        // Permission already checked above, ignore the warning with:
                        //      @SuppressLint("MissingPermission")
                        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

                        // Keep CPU on
                        wakeLock.acquire();

                        // retrieves the number in EditText sample rate
                        sampleRate = 50;//Integer.parseInt(editTextSampleRate.getText().toString());

                        // Retrieves sample in accordance to the number taken from sample rate.
                        // However, the thread does not function exactly like that number.
                        // For example, if you ask for 50 samples, sometimes it will only take 45 samples. If 100 samples, sometimes it will only take 80.
                        // Therefore, 10% is added to the sample rate that was taken from the EditText sample rate.
                        sampleRate = (int) (1000 / (sampleRate + (0.1 * sampleRate)));

                        // Preparing file
                        try {
                            newFile = new File(Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() +
                                    "/SensorReaderData/", fileName + ".csv");
                            if (!newFile.exists()) {
                                newFile.createNewFile();
                            }
                            fw = new FileWriter(newFile);
                            bw = new BufferedWriter(fw);
                            Log.d("Fragment1", "new bw and fw created");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        buttonStart.setText(R.string.button_stop);
                        useHandler();

                    } else {
                        textViewRecording.setText("");
                        buttonStart.setBackgroundColor(ContextCompat.getColor(requireContext(),
                                R.color.colorPrimary));
                        chipMotorcycle.setCheckable(true);
                        chipBicycle.setCheckable(true);
                        chipCar.setCheckable(true);
                        buttonUploadActivity.setEnabled(true);
                        //buttonStart.getBackground().clearColorFilter();
                        //buttonStart.setBackgroundResource(android.R.drawable.btn_default);

                        textViewTime.setText("00:00:00");
                        buttonStart.setText(R.string.button_start);
                        try {
                            fw.close();
                            bw.close();
                            Log.d("Fragment1", "bw closed");
                        } catch (IOException ioe) {
                            Log.d("Fragment1", "bw fail to close:" + ioe);
                        }
                        mHandler.removeCallbacks(mRunnable);
                        ///fusedLocationProviderClient.removeLocationUpdates(locationCallback);
                        if (wakeLock.isHeld()) {
                            wakeLock.release();
                        }
                        //int i=3;
                        stopTheService();
                    }
                }
            }
        });

        buttonUploadActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(requireActivity(), UploadActivity.class));
            }
        });
        return v;
    }

    public void startTheService(){
        Intent intent = new Intent(requireContext().getApplicationContext(), ReaderService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(intent);
        }
        else{
            requireActivity().startService(intent);
        }
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        //ContextCompat.startForegroundService(this, intent);
    }

    public void stopTheService(){
        Intent intent = new Intent(requireContext().getApplicationContext(), ReaderService.class);
        requireActivity().unbindService(serviceConnection);
        requireActivity().stopService(intent);

    }

    private void builtLocationCallback() {
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location: locationResult.getLocations()) {
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();
                    speed = location.getSpeed();
                }
            }
        };
    }

    private void buildLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(0)
                .setMinUpdateIntervalMillis(0)
                .setMinUpdateDistanceMeters(0.0f)
                .build();
    }

    private boolean checkGPSStatus(){
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            AlertDialog.Builder ad = new AlertDialog.Builder(getActivity());
            Intent intentAd = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intentAd); // redirect to location setting

            // check if GPS is off. If it is off, start turning it on
            ad.setIcon(android.R.drawable.ic_dialog_alert);
            ad.setTitle(R.string.warning_title);
            ad.setMessage(R.string.warning_gps_off);
            ad.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    checkPermissions();
                }
            });
        }
        return true;
    }

/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

 */

    @Override
    public void onResume() {
        super.onResume();

        boolean gpsStat = checkGPSStatus();
        Log.d("Fragment1", "is gps on?:" + gpsStat);

        // get location permissions
        boolean permission = checkPermissions();
        Log.d("Fragment1", "Location permission:" + permission);

        sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorGyr, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorMag, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorGra, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accX = sensorEvent.values[0];
                accY = sensorEvent.values[1];
                accZ = sensorEvent.values[2];



                mAccelerometerData = sensorEvent.values.clone();
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyrX = sensorEvent.values[0];
                gyrY = sensorEvent.values[1];
                gyrZ = sensorEvent.values[2];



                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                /*
                magX = sensorEvent.values[0];
                magY = sensorEvent.values[1];
                magZ = sensorEvent.values[2];

                // To calculate Azimuth, Pitch, dan Roll
                mMagnetometerData = sensorEvent.values.clone();
                float[] rotationMatrix = new float[9];
                boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix,null, mAccelerometerData, mMagnetometerData);
                float[] orientationValues = new float[3];
                if (rotationOK) {
                    SensorManager.getOrientation(rotationMatrix, orientationValues);
                }

                magA = orientationValues[0];
                magP = orientationValues[1];
                magR = orientationValues[2];

                editTextMagX.setText(String.valueOf((double)Math.round(sensorEvent.values[0]*10000)/10000));
                editTextMagY.setText(String.valueOf((double)Math.round(sensorEvent.values[1]*10000)/10000));
                editTextMagZ.setText(String.valueOf((double)Math.round(sensorEvent.values[2]*10000)/10000));

                editTextMagA.setText(String.valueOf(orientationValues[0]));
                editTextMagP.setText(String.valueOf(orientationValues[1]));
                editTextMagR.setText(String.valueOf(orientationValues[2]));
                */
                break;
            case Sensor.TYPE_GRAVITY:
                /*
                graX = sensorEvent.values[0];
                graY = sensorEvent.values[1];
                graZ = sensorEvent.values[2];


                editTextGyrX.setText(String.valueOf((double)Math.round(sensorEvent.values[0]*10000)/10000));
                editTextGyrY.setText(String.valueOf((double)Math.round(sensorEvent.values[1]*10000)/10000));
                editTextGyrZ.setText(String.valueOf((double)Math.round(sensorEvent.values[2]*10000)/10000));
                */
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    // TODO: Do we need checks if app or service is on foreground?
    /*
    private boolean isAppOnForeground(Context context, String appPackageName) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = appPackageName;
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                //                Log.e("app",appPackageName);
                return true;
            }
        }
        return false;
    }

    public static boolean isServiceRunningInForeground(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }
    */
}