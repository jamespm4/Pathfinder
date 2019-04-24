package com.example.runplot;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.math.RoundingMode;
import java.security.Security;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/*
 * Good names for this app:
 * Pathfinder
 * Trailblazer
 *
 */

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;

    private boolean locationGranted;
    private boolean locationAccessed = false;

    private final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 420;

    private List<Marker> nodes = new ArrayList<>();
    private PolylineOptions route = new PolylineOptions().geodesic(true).color(0xff1254dc).width(14f);
    private Polyline pathLine;
    private Marker tempMarker;
    private Marker insertMarker;

    private double EARTH_RADIUS_MILES = 3958.756;

    private int drawMode = 0;
    // 0 = add nodes normally
    // 1 = erase nodes
    // 2 = insert nodes before a selected node
    private int awaiting = 0;
    // 0 = awaiting no action
    // 1 = awaiting a marker click
    // 2 = awaiting a map click
    // This variable should only be nonzero if drawMode is 2.

    private float selectedAlpha = 1.0f;
    private float unselectedAlpha = 0.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        getLocationPermission();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        final ImageButton addButton = findViewById(R.id.btn_add);
        final ImageButton eraseButton = findViewById(R.id.btn_erase);
        final ImageButton insertButton = findViewById(R.id.btn_insert);
        final ImageButton confirmButton = findViewById(R.id.btn_confirm);
        final TextView textOne = findViewById(R.id.txt_info1);
        final TextView textTwo = findViewById(R.id.txt_info2);

        mMap = googleMap;
        //MapsInitializer.initialize(Context);

        if (locationGranted) {
            try {
                mMap.setMyLocationEnabled(true);
                locationAccessed = true;
            } catch (SecurityException e) {
                //The app failed to enable the myLocation layer for Google Maps.
                //This really should never happen
                //Just do nothing
            }
        }
        //mMap.getUiSettings().setMyLocationButtonEnabled(true);

        //The block of code below is just here so that the app zooms in on your location when it starts up.
        if (locationAccessed) {
            try {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                            new LatLng(location.getLatitude(), location.getLongitude()), 17.0f));
                                }
                            }
                        });
            } catch (SecurityException e) {
                //The phone couldn't access your location even though you gave it permission to.
                //I don't know, do nothing? I guess?
            }
        }

        //Old Test Code VVV
        // Add a marker in Sydney and move the camera
        //LatLng sydney = new LatLng(-34, 151);
        //mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        /*
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .title("Yeet")
                .rotation(40)
                .alpha(0.8f)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        */

        //Add a marker when the map is clicked.
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {

                if (drawMode == 0) { //regular draw
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(point)
                            .rotation(30)
                            .alpha(0.75f)
                            .draggable(true)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(point), 250, null);
                    nodes.add(marker);
                    route.add(point);
                    if (pathLine != null) {
                        pathLine.remove();
                    }
                    pathLine = mMap.addPolyline(route);

                    updateDistance();

                } else if (drawMode == 2) { //insert
                    if (nodes.size() > 0) {
                        if (awaiting == 0) {
                            tempMarker = mMap.addMarker(new MarkerOptions()
                                    .position(point)
                                    .rotation(-30)
                                    .alpha(0.75f)
                                    .draggable(true)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                            textOne.setText("Select a marker");
                            textTwo.setText("to insert this stop behind.");
                            awaiting = 1;
                        } else if (awaiting == 2) {
                            //The user already clicked a marker, and is now clicking where the new marker should be
                            Marker newMarker = mMap.addMarker(new MarkerOptions()
                                    .position(point)
                                    .rotation(30)
                                    .alpha(0.75f)
                                    .draggable(true)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                            for (int n = 0; n < nodes.size(); n++) {
                                if (nodes.get(n).equals(insertMarker)) {
                                    nodes.add(n, newMarker);
                                    break;
                                }
                            }
                            if (tempMarker != null) {
                                tempMarker.remove();
                            }
                            awaiting = 0;
                            refreshRoute();
                            textOne.setText("");
                            textTwo.setText("");
                        }
                    }
                }
            }
        });

        //Add or remove a marker when one is clicked.
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            public boolean onMarkerClick(Marker marker) {
                if (drawMode == 0) { //regular draw
                    nodes.add(marker);
                    route.add(marker.getPosition());
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()), 250, null);
                    if (pathLine != null) {
                        pathLine.remove();
                    }
                    pathLine = mMap.addPolyline(route);
                    updateDistance();

                } else if (drawMode == 1) { //erase
                    for (int n = nodes.size() - 1; n >= 0; n--) {
                        if (marker.equals(nodes.get(n))) {
                            nodes.remove(n);
                        }
                    }
                    marker.remove();
                    refreshRoute();

                } else if (drawMode == 2) { //insert
                    if (!(marker.equals(tempMarker))) {
                        if (awaiting == 0) {
                            LatLng spot = marker.getPosition();
                            tempMarker = mMap.addMarker(new MarkerOptions()
                                    .position(spot)
                                    .rotation(-30)
                                    .alpha(0.75f)
                                    .draggable(true)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                            insertMarker = marker;
                            textOne.setText("Insert a stop");
                            textTwo.setText("behind this marker.");
                            awaiting = 2;
                        } else if (awaiting == 1) {
                            for (int n = 0; n < nodes.size(); n++) {
                                if (marker.equals(nodes.get(n))) {
                                    Marker newMarker = mMap.addMarker(new MarkerOptions()
                                            .position(tempMarker.getPosition())
                                            .rotation(30)
                                            .alpha(0.75f)
                                            .draggable(true)
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                                    nodes.add(n, newMarker);
                                    break;
                                }
                            }
                            if (tempMarker != null) {
                                tempMarker.remove();
                            }
                            awaiting = 0;
                            refreshRoute();
                            textOne.setText("");
                            textTwo.setText("");
                        }
                    }
                }
                return true;
            }
        });

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            public void onMarkerDragStart(Marker marker) {
                //yeet
            }
            public void onMarkerDrag(Marker marker) {
                //yeet
            }
            @Override
            public void onMarkerDragEnd(Marker marker) {
                //Redraw route
                refreshRoute();
            }
        });

        //Code that runs when the pencil button is pressed.
        addButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (awaiting == 0) {
                    drawMode = 0;
                    eraseButton.setAlpha(unselectedAlpha);
                    insertButton.setAlpha(unselectedAlpha);
                    addButton.setAlpha(selectedAlpha);
                }
            }
        });

        //Code that runs when the eraser button is pressed.
        eraseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (awaiting == 0) {
                    drawMode = 1;
                    addButton.setAlpha(unselectedAlpha);
                    insertButton.setAlpha(unselectedAlpha);
                    eraseButton.setAlpha(selectedAlpha);
                }
            }
        });

        //Code that runs when the insert button is pressed.
        insertButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (nodes.size() > 0) {
                    drawMode = 2;
                    addButton.setAlpha(unselectedAlpha);
                    eraseButton.setAlpha(unselectedAlpha);
                    insertButton.setAlpha(selectedAlpha);
                }
            }
        });
    }

    //Attempts to get the app permission to use location data.
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationGranted = true;
        } else {
            /* Permission to use the phone's location isn't already granted */
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    //If the app doesn't already have permission to use location data,
    //ask the user for it.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationGranted = true;
                } else {
                    locationGranted = false;
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    //Returns the length of the route based on the 'nodes' list.
    private double calculateDistance() {
        int length = nodes.size();
        if (length <= 1) {
            return 0.0;
        }
        double lastLat = Math.toRadians(nodes.get(0).getPosition().latitude);
        double lastLong = Math.toRadians(nodes.get(0).getPosition().longitude);
        double newLat, newLong;
        double arc;
        double distance = 0.0;
        for (int n = 1; n < length; n++) {
            newLat = Math.toRadians(nodes.get(n).getPosition().latitude);
            newLong = Math.toRadians(nodes.get(n).getPosition().longitude);
            arc = Math.acos((Math.sin(lastLat) * Math.sin(newLat)) + (Math.cos(lastLat) * Math.cos(newLat) * Math.cos(newLong - lastLong)));
            distance += arc * EARTH_RADIUS_MILES;
            lastLat = newLat;
            lastLong = newLong;
        }
        return distance;
    }

    //Updates the path based on the order of the markers in the 'nodes' list.
    private void refreshRoute() {
        if (pathLine != null) {
            pathLine.remove();
        }
        route = new PolylineOptions().geodesic(true).color(0xff1254dc).width(14f);
        for (int n = 0; n < nodes.size(); n++) {
            route.add(nodes.get(n).getPosition());
        }
        pathLine = mMap.addPolyline(route);
        updateDistance();
    }

    //Recalculates the length of the route, and updates the UI.
    private void updateDistance() {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(2);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        String dist = nf.format(calculateDistance());

        TextView textView = findViewById(R.id.txt_distance);
        textView.setText(dist + " miles");
    }
}
