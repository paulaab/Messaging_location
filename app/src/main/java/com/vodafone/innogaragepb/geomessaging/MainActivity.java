package com.vodafone.innogaragepb.geomessaging;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


import android.net.wifi.WifiManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.String;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;


public class MainActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener{
    /*----------------Initialize Variables - MAPS ----------------*/
    private GoogleMap mMap;
    public Boolean ready;
    public Marker myMarker;
    public LatLng myLatLng;
    public LatLng myLatLng2;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Marker mCurrentLocation;
    Location mLastLocation;

    /*---------------Initialize Variables - MSG ---------------------*/
    //Define Variables
    private Handler handler = new Handler();
    public ListView msgView;
    public ArrayAdapter<String> msgList;
    protected Context context;
    private static WifiManager wifi_manager;
    protected int PORT_NUM = 5432;
    private static final String MESSAGE = "Hello World!";
    private static final String MCAST_ADDR = "FF02::1";//"FF01::101"; //IPV6 ADDRESS
    private static InetAddress GROUP;
    private MulticastSocket mcSocketSend;
    private DatagramPacket mcPacketSend;
    public MulticastSocket mcSocketRecv = new MulticastSocket(PORT_NUM);
    volatile boolean shutdown = false;

    public MainActivity() throws IOException {
        System.out.println("Could not set up recv socket");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
/*-------------GOOGLE MAPS Initialization--------------------*/
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

// Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

/*---------------View initialization - MSG-----------------*/
        msgView = (ListView) findViewById(R.id.listView);
        msgList = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);
        msgView.setAdapter(msgList);
        //final EditText txtEdit = (EditText) findViewById(R.id.myMsg);

/*---------------Creating JSON Object-------------------*/
        final JSONObject myJO = new JSONObject();
        JSONArray jarr = new JSONArray();
        //JSON Object for testing purposes
        try {
            myJO.put("MessageTypeID", 2);
            myJO.put("Erzeugerzeitpunkt", 12);
            myJO.put("Lebensdauer", 12);
            myJO.put("Lat", 51.23610018);
            myJO.put("Long",6.73155069);
            myJO.put("Cell ID",jarr);
            myJO.put("Message", "hello");
        } catch (JSONException e) {
            e.printStackTrace();
        }



/*---------------Initialize buttons - MAPS ---------------------*/
        final Button accidentButton = (Button) findViewById(R.id.accidentButton);
        final Button trafficjamButton = (Button) findViewById(R.id.trafficjamButton);
        final Button speedlimitButton = (Button) findViewById(R.id.speedlimitButton);
/*--------------------Initialize buttons - MSG ------------------------*/
        final Button sendButton = (Button) findViewById(R.id.sendButton);
        final Button recvButton = (Button) findViewById(R.id.receiveButton);
        final Button stopButton = (Button) findViewById(R.id.stopButton);
/*-------------------Setting on-click listener for all buttons----------*/


        accidentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Place a marker with location and time to live
                setSituation(5000, myLatLng, myMarker, "accident");
            }
        });

        trafficjamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Sendtraffic icon
                setSituation(10000, myLatLng2, myMarker, "trafficjam");
            }
        });

        speedlimitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Sendtraffic icon
                setLocation(5000, myLatLng2, myMarker, "pink");
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sendMessage("Hello world");
                //sendMessage(txtEdit.getText().toString());
                sendMessage(myJO.toString());
            }
        });

        recvButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shutdown = false;
                try {
                    receiveMessage();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                try {
                    mcSocketRecv.leaveGroup(GROUP);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                shutdown = true;
            }
        });




    }
/*--------------------------------GOOGLE MAPS METHODS------------------------------*/
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        myLatLng = new LatLng(51.23610018, 6.73155069);
        myLatLng2 = new LatLng(51.23708092, 6.72972679);
        // Initialize Google Play Services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        mMap.getUiSettings().setZoomControlsEnabled(true);
        ready = true;
    }

    //SET USE CASES: Situation in the traffic or just location of the other cars
    public void setSituation(long duration, LatLng myLatLng, Marker marker, String myString) {
        final String code = myString;
        if (ready) {
            marker = mMap.addMarker(new MarkerOptions()
                    .position(myLatLng)
                    .icon(BitmapDescriptorFactory.fromBitmap(resizer(code, 70, 70))));
            fadeTime(duration, marker);
        }
    }

    public void setLocation (long duration, LatLng myLatLng, Marker marker, String cellColor){
        if (ready) {
            int id = getResources().getIdentifier(cellColor, "drawable", getPackageName());
            marker = mMap.addMarker(new MarkerOptions()
                    .position(myLatLng)
                    .icon(BitmapDescriptorFactory.fromResource(id)));
            fadeTime(duration, marker);
        }
    }

//Customize characteristics of the markers: Size and time to fade

    public Bitmap resizer(String iconName, int width, int height) {
        Bitmap imgBitmap = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(iconName, "drawable", getPackageName()));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imgBitmap, width, height, false);
        return resizedBitmap;
    }

    public void fadeTime(long duration, Marker marker) {
        final Marker myMarker = marker;
        final LinearInterpolator inter = new LinearInterpolator();
        ValueAnimator myAnim = ValueAnimator.ofFloat(1, 0);
        myAnim.setDuration(duration);
        myAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                myMarker.setAlpha((float) animation.getAnimatedValue());
            }
        });
        myAnim.start();
    }


    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        if (mCurrentLocation != null){
            mCurrentLocation.remove();
        }
        //Place my location Marker
        LatLng latlong = new LatLng(location.getLatitude(), location.getLongitude());
        //mCurrentLocation = mMap.addMarker(new MarkerOptions()
        //  .position(latlong));
        //.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)));

        //move map camera
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latlong));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

        //stop location updates
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, (com.google.android.gms.location.LocationListener) this);
        }
    }



    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest,this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Permission Granted
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }


/*-----------------------------MESSAGING METHODS--------------------------------*/

/*-------------------------Sending ------------------------------------------*/


    public void sendMessage(String message) throws IllegalArgumentException {
        if (message == null || message.length() == 0) {
            throw new IllegalArgumentException();
        }
        final String mensaje = message;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Create the Multicast sending socket and join Multicast Group
                if (mcSocketSend == null) {
                    try {
                        GROUP = InetAddress.getByName(MCAST_ADDR);
                        mcSocketSend = new MulticastSocket(PORT_NUM);
                        //Use in case of IPv6 problems on Samsung...
                        NetworkInterface nif = NetworkInterface.getByName("wlan0");
                        if (null != nif) {
                            System.out.println( "picking interface "+nif.getName()+" for transmit");
                            mcSocketSend.setNetworkInterface(nif);
                        }
                        //...Until here.
                        mcSocketSend.joinGroup(GROUP);

                    } catch (Exception e) {
                        Log.d("Error in the socket: ", e.getMessage());

                    }
                }

//Build the Datagram Packet

                try {
                    mcPacketSend = new DatagramPacket(mensaje.getBytes(), mensaje.length(), GROUP, PORT_NUM);

                } catch (Exception e) {
                    Log.v("Error creating packet: ", e.getMessage());
                }

//Send the packet
                try {
                    mcSocketSend.send(mcPacketSend);
                } catch (IOException e) {
                    System.out.println("There was an error sending the packet");
                    e.printStackTrace();
                }
                System.out.println("Server sent packet with msg: " + mensaje);
            }
        }).start();
    }

/*-------------------------Receiving methods----------------------------------------*/




    public void receiveMessage() throws UnknownHostException {
        giveLock();
        // Get the address of the group that we are going to connect to
        final InetAddress addressGroup = InetAddress.getByName(MCAST_ADDR);
        System.out.println("Inside method");
        new Thread(new Runnable() {
            @Override
            public void run() {


//Create the Multicast receiving socket and join Multicast Group

                try {
                    //Use in case of IPv6 problems on Samsung...
                    NetworkInterface nif = NetworkInterface.getByName("wlan0");
                    if (null != nif) {
                        System.out.println( "picking interface "+nif.getName()+" for transmit");
                        mcSocketRecv.setNetworkInterface(nif);
                    }
                    //...Until here.
                    mcSocketRecv.joinGroup(addressGroup);

                    while (!shutdown) {
                        // Create a buffer of bytes, which will be used to store incoming messages
                        byte[] buffer = new byte[256];
                        // Receive the info on a socket and print it on the screen
                        DatagramPacket mcPacketRecv = new DatagramPacket(buffer, buffer.length);
                        mcSocketRecv.receive(mcPacketRecv);
                        String msg = new String(buffer, 0, buffer.length);
                        System.out.println("Socket 1 received msg: " + msg);
                        displayMsg(msg);

                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();

    }


    public void giveLock () {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();
    }
/*-----------------------Display my message--------------------------------*/

    public void displayMsg(String msg) {
        if (!shutdown) {
            final String mensajeRecibido = msg;
            System.out.println("Inside dispmsg");


            handler.post(new Runnable() {
                @Override
                public void run() {
                    //TODO Auto-generated method stub
                    System.out.println("Inside Handler start");
                    msgList.add(mensajeRecibido);
                    msgView.setAdapter(msgList);
                    msgView.smoothScrollToPosition(msgList.getCount() - 1);
                    System.out.println("Inside Handler fertig");
                }
            });

        }
    }
}
