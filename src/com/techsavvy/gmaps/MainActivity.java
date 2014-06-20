package com.techsavvy.gmaps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

@SuppressLint("NewApi")
public class MainActivity extends Activity implements LocationListener {

	private int userIcon, foodIcon, atmIcon;
	private GoogleMap theMap;
	private LocationManager locMan;
	private Marker userMarker;
	private Marker[] placeMarkers;
	private final int MAX_PLACES = 12;
	private MarkerOptions[] places;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		userIcon = R.drawable.user;
		atmIcon = R.drawable.atm;
		foodIcon = R.drawable.food;
		locMan = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		if(!locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)){
			buildAlertMessageNoGps();
		}
		if(theMap==null){
			theMap = ((MapFragment)getFragmentManager().findFragmentById(R.id.the_map)).getMap();
			if(theMap != null){
			    theMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
			    theMap.setTrafficEnabled(true);
			    theMap.setBuildingsEnabled(true);
			    placeMarkers = new Marker[MAX_PLACES];
			    updatePlaces();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void updatePlaces(){
		//update location
		
		Location lastLoc = locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		double lat = lastLoc.getLatitude();
		double lng = lastLoc.getLongitude();
		LatLng lastLatLng = new LatLng(lat, lng);
		if(userMarker!=null) userMarker.remove();
		userMarker = theMap.addMarker(new MarkerOptions()
	    .position(lastLatLng)
	    .title("You are here")
	    .icon(BitmapDescriptorFactory.fromResource(userIcon))
	    .snippet("Your last recorded location"));
		theMap.animateCamera(CameraUpdateFactory.newLatLng(lastLatLng), 3000, null);
		String placesSearchStr = "https://maps.googleapis.com/maps/api/place/nearbysearch/"+
			    "json?location="+lat+","+lng+
			    "&radius=1000&sensor=true" +
			    "&types=atm"+
			    "&key=AIzaSyBvjVH-U4G4l6UTYis5dn4H0H9wWZgD-SU";
		Log.d("chutiya",placesSearchStr);
		new GetPlaces().execute(placesSearchStr);
		
		locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 100, this);
		}
	
	private void buildAlertMessageNoGps() {
		// TODO Auto-generated method stub
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
	           .setCancelable(false)
	           .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	               public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	                   startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
	               }
	           })
	           .setNegativeButton("No", new DialogInterface.OnClickListener() {
	               public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	                    dialog.cancel();
	               }
	           });
	    final AlertDialog alert = builder.create();
	    alert.show();
	}

	private class GetPlaces extends AsyncTask<String, Void, String> {

		@Override
		protected String doInBackground(String... params) {
			// TODO Auto-generated method stub
			StringBuilder placesBuilder = new StringBuilder();
			for (String placeSearchURL : params) {
			
				HttpClient placesClient = new DefaultHttpClient();
				try {
					//Log.d("dsdsds", placeSearchURL);
					HttpGet placesGet = new HttpGet(placeSearchURL);
					HttpResponse placesResponse = placesClient.execute(placesGet);
					StatusLine placeSearchStatus = placesResponse.getStatusLine();
					Log.d("fsdfsdfsdfds", placeSearchStatus.toString());
					if (placeSearchStatus.getStatusCode() == 200) {
						//we have an OK response
						HttpEntity placesEntity = placesResponse.getEntity();
						//Log.d("dfdsfvvvvvv", placesResponse.toString());
						InputStream placesContent = placesEntity.getContent();
						InputStreamReader placesInput = new InputStreamReader(placesContent);
						BufferedReader placesReader = new BufferedReader(placesInput);
						String lineIn;
						while ((lineIn = placesReader.readLine()) != null) {
						    placesBuilder.append(lineIn);
						}
						}
				}
				catch(Exception e){
				    e.printStackTrace();
				}
				}
			return placesBuilder.toString();
			
		}
		
		protected void onPostExecute(String result) {
			if(placeMarkers!=null){
			    for(int pm=0; pm<placeMarkers.length; pm++){
			        if(placeMarkers[pm]!=null)
			            placeMarkers[pm].remove();
			    }
			}
			try {
			    //parse JSON
				JSONObject resultObject = new JSONObject(result);
				//Log.d("kikukikukjdsdvsdvcds", "arjitagarwal099" + resultObject.toString() );
				JSONArray placesArray = resultObject.getJSONArray("results");
				places = new MarkerOptions[placesArray.length()];
				for (int p=0; p<placesArray.length(); p++) {
					boolean missingValue=false;
					LatLng placeLL=null;
					String placeName="";
					String vicinity="";
					int currIcon = foodIcon;
					try {
						missingValue=false;
						JSONObject placeObject = placesArray.getJSONObject(p);
						JSONObject loc = placeObject.getJSONObject("geometry").getJSONObject("location");
						placeLL = new LatLng(
							    Double.valueOf(loc.getString("lat")),
							    Double.valueOf(loc.getString("lng")));
						JSONArray types = placeObject.getJSONArray("types");
						//Log.d("kukdi", "techsavvy 00" + placeLL.toString());
						for(int t=0; t<types.length(); t++){
							String thisType=types.get(t).toString();

						
							if(thisType.contains("food")){
							    currIcon = foodIcon;
							    break;
							}
							else if(thisType.contains("atm")){
							    currIcon = atmIcon;
							    break;
							}
						}
						vicinity = placeObject.getString("vicinity");
						//name
						placeName = placeObject.getString("name");
					
					} catch(JSONException jse){
					    missingValue=true;
					    jse.printStackTrace();
					}
					if(missingValue)	places[p]=null;
					else
						places[p]=new MarkerOptions()
					.position(placeLL)
					.title(placeName)
					.icon(BitmapDescriptorFactory.fromResource(currIcon))
					.snippet(vicinity);
			}
			}
			catch (Exception e) {
			    e.printStackTrace();
			}
			if(places!=null && placeMarkers!=null){
				for(int p=0; p<places.length && p<placeMarkers.length; p++){
					//will be null if a value was missing
					if(places[p]!=null)
						placeMarkers[p]=theMap.addMarker(places[p]);
				}

			}
		//fetch and parse place data
		

		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if(theMap!=null){
			locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 100, this);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if(theMap!=null){
			locMan.removeUpdates(this);
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub
		Log.v("MyMapActivity", "location changed");
	    updatePlaces();
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		Log.v("MyMapActivity", "provider disabled");
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		Log.v("MyMapActivity", "provider enabled");
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		Log.v("MyMapActivity", "status changed");
	}
}
