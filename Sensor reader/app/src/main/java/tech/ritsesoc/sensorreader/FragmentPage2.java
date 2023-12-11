/*
 * FragmentPage2: Map
 * For displaying the map visualization
 * (Currently only displays the map, not the defect points)
 */

package tech.ritsesoc.sensorreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

import tech.ritsesoc.sensorreader.R;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class FragmentPage2 extends Fragment {
    private Button button2;

    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView map = null;
    FloatingActionButton buttonCenterMap;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private double latitude, longitude;
    private LocationManager locationManager;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private IMapController mapController;
    private CustomZoomButtonsController mapZoomController;


    // requestPermissionLauncher and checkPermissions():
    // Perform Runtime Permission checks and requests
    // (This FragmentPage only requires location permissions)
    // Returns boolean true if successful.
    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
        Log.d("Permissions", "Launcher result: " + isGranted.toString());
        if (isGranted.containsValue(false)) {
            checkPermissions();
        }
    });
    public boolean checkPermissions() {
        // Runtime Permissions are not needed before Android Marshmallow, only install-time permissions.
        // If below Marshmallow, cancel the request.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        AlertDialog.Builder ad = new AlertDialog.Builder(requireContext());
        ad.setIcon(android.R.drawable.ic_dialog_alert);
        ad.setTitle(R.string.warning_title);

        ArrayList<String> requestPermissions = new ArrayList<String>();

        // LOCATION PERMISSIONS
        // FINE location must be asked together with COARSE location
        // BACKGROUND location is only needed for Android 10 (APK 29) or higher
        if (requireActivity().checkSelfPermission(ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.add(ACCESS_COARSE_LOCATION);
        }
        if (requireActivity().checkSelfPermission(ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.add(ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (requireActivity().checkSelfPermission(ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(ACCESS_BACKGROUND_LOCATION);
            }
        }

        // Launch permission request
        if (requestPermissions.size() > 0) {
            ad.setMessage(R.string.warning_location_storage_permission);
            ad.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String[] location_reqperm = requestPermissions.toArray(new String[requestPermissions.size()]);
                    requestPermissionLauncher.launch(location_reqperm);
                }
            });
            AlertDialog alertDialog = ad.create();
            alertDialog.show();
        } else {
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_page2, container, false);
        buttonCenterMap = (FloatingActionButton) v.findViewById(R.id.buttonCenterMap);
        map = (MapView) v.findViewById(R.id.map);

        Context ctx = requireContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Setting this before the layout is inflated is a good idea
        // It *should* ensure that the map has a writable location for the map cache, even without permissions
        // If no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        // See also StorageUtils
        // NOTE: the load method also sets the HTTP User Agent to your application's package name, abusing OSM's
        // tile servers will get you banned based on this string

        // Inflate and create the map
        //setContentView(R.layout.activity_main);

        map = (MapView) v.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        // TODO: find alternatives to zoom controls
        mapZoomController = map.getZoomController();
        mapZoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        map.setMultiTouchControls(true);
        map.setMaxZoomLevel(19.0);
        map.setMinZoomLevel(17.0);

        RotationGestureOverlay mRotationGestureOverlay = new RotationGestureOverlay(requireContext(), map);
        mRotationGestureOverlay.setEnabled(true);
        map.getOverlays().add(mRotationGestureOverlay);

        // Displays map on a specific coordinate.
        mapController = map.getController();
        mapController.setZoom(17.0);

        locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);

        buildLocationRequest();
        builtLocationCallback();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext());
        GeoPoint startPoint;
        if ((latitude == 0)&&(longitude == 0)){
            startPoint = new GeoPoint(-7.982490, 112.630160);
        }
        else{
            startPoint = new GeoPoint(latitude, longitude);
        }

        mapController.setCenter(startPoint);


        // My Location overlay
        MyLocationNewOverlay mLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()),map);
        mLocationOverlay.enableMyLocation();
        map.getOverlays().add(mLocationOverlay);

        // TODO: clean up this part
        // Set how the "center map to your location" button on top left behaves
        buttonCenterMap.setOnClickListener(new View.OnClickListener(){
               @SuppressLint("MissingPermission")
               @Override
               public void onClick(View v) {
                   // TODO: the built-in dialogue does not work for location permission, why?
                   // check if you get location permissions or not
                   boolean permission = checkPermissions();

                   // if location permission is allowed, set current location to gps location
                   // else
                   if (permission){
                       fusedLocationProviderClient.requestLocationUpdates(
                               locationRequest, locationCallback, Looper.myLooper());
                       mapController.setCenter(new GeoPoint(latitude, longitude));
                   } else {
                       mapController.setCenter(new GeoPoint(-7.982490, 112.630160));

                       //Log.i("Loc:",latitude+","+longitude);
                       //map.getOverlays().remove(currentPositionMarker);
                       //remarkLocation(latitude,longitude);
                       //addMarker(34.988615,135.950532, R.drawable.ic_stat);
                   }
               }
        });
        return v;
    }


    @Override
    public void onResume() {
        super.onResume();
        // This will refresh the osmdroid configuration on resuming.
        // If you make changes to the configuration, use
        // SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onPause() {
        super.onPause();
        // This will refresh the osmdroid configuration on resuming.
        // If you make changes to the configuration, use
        // SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

    // TODO: already replaced with checkPermissions(), find the remaining calls to this
//    @Override
//    public void setUserVisibleHint(boolean isVisibleToUser) {
//        super.setUserVisibleHint(isVisibleToUser);
//        if (isVisibleToUser){
//            boolean permission = true;
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                permission = (requireActivity().checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
//            }
//
//            if (!permission) {
//                //Toast.makeText(requireActivity(),"Permission tidak aktif",Toast.LENGTH_LONG).show();
//                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(requireActivity());
//                // Set title
//                //alertDialogBuilder.setTitle("Are you sure to Cancel the Subscription ?");
//                alertDialogBuilder.setTitle("Peringatan");
//                alertDialogBuilder.setMessage("Ijinkan aplikasi untuk mengakses media penyimpanan agar aplikasi dapat membaca peta yang tersimapn di perangkat pengguna, untuk meminimalkan komunikasi data");
//                // Set dialog message
//                alertDialogBuilder
//                        //.setMessage("After cancelling you will automatically got logout and then you have to buy subscription again")
//                        .setCancelable(true)
//                        .setPositiveButton("settings", new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int id) {
//                                Intent intent = new Intent();
//                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                                Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
//                                intent.setData(uri);
//                                startActivity(intent);
//                            }
//                        })
//                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int id) {
//                                dialog.cancel();
//                            }
//                        });
//                AlertDialog alertDialog = alertDialogBuilder.create();
//                alertDialog.show();
//
//                //textViewUploadedFile.setText("Tidak bisa membaca file, akses ke media penyimpanan belum diijinkan pengguna");
//                //buttonUpload.setEnabled(false);
//            } else {
//                //showFile();
//            }
//        }
//    }

//    private void requestPermissionsIfNecessary(String[] permissions) {
//        ArrayList<String> permissionsToRequest = new ArrayList<>();
//        for (String permission : permissions) {
//            if (ContextCompat.checkSelfPermission(requireContext(), permission)
//                    != PackageManager.PERMISSION_GRANTED) {
//                // Permission is not granted
//                permissionsToRequest.add(permission);
//            }
//        }
//        if (permissionsToRequest.size() > 0) {
//            ActivityCompat.requestPermissions(
//                    requireActivity(),
//                    permissionsToRequest.toArray(new String[0]),
//                    REQUEST_PERMISSIONS_REQUEST_CODE);
//        }
//    }

    private void builtLocationCallback() {
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location: locationResult.getLocations()) {
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();
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
}