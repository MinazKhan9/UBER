package com.example.ridersapp.ui.home

import android.Manifest
import android.content.Intent
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ridersapp.Callback.FirebaseDriverInfoListener
import com.example.ridersapp.Callback.FirebaseFailedListener
import com.example.ridersapp.Common.Common
import com.example.ridersapp.Model.DriverGeoModel
import com.example.ridersapp.Model.DriverInfoModel
import com.example.ridersapp.Model.EventBus.SelectPlaceEvent
import com.example.ridersapp.Model.GeoQueryModel
import com.example.ridersapp.R
import com.example.ridersapp.RequestDriverActivity
import com.example.ridersapp.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.util.Arrays
import java.util.Locale

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {
    private var _binding: FragmentHomeBinding? = null
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var homeViewModel: HomeViewModel

    private lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
    private lateinit var txt_welcome: TextView
    private lateinit var autocompleteSupportFragment: AutocompleteSupportFragment

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null

    var firstTime = true

    lateinit var iFirebaseDriverInfoListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedListener

    var cityName = ""


    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        init()
        initViews(root)

        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    private fun initViews(root: View?) {
        slidingUpPanelLayout = root!!.findViewById(R.id.activity_main) as SlidingUpPanelLayout
        txt_welcome = root.findViewById(R.id.txt_welcome) as TextView

        Common.setWelcomeMessage(txt_welcome)
    }

    private fun init() {

        Places.initialize(requireContext(), getString(R.string.google_api_key))
        autocompleteSupportFragment =
            childFragmentManager.findFragmentById(R.id.autoComplete_fragment) as AutocompleteSupportFragment
        autocompleteSupportFragment.setPlaceFields(
            Arrays.asList(
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.NAME
            )
        )
        autocompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onError(p0: Status) {
                Snackbar.make(requireView(), ""+p0.statusMessage!!, Snackbar.LENGTH_LONG).show()
            }

            override fun onPlaceSelected(p0: Place) {
               // Snackbar.make(requireView(), "" + p0.latLng!!, Snackbar.LENGTH_LONG).show()
                fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                    val origin = LatLng(location.latitude,location.longitude)
                    val destination = LatLng(p0.latLng!!.latitude,p0.latLng!!.longitude)
                    startActivity(Intent(requireContext(),RequestDriverActivity::class.java))
                    EventBus.getDefault().postSticky(SelectPlaceEvent(origin,destination))
                }
            }

        })

        iFirebaseDriverInfoListener = this

        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.interval = 5000
        locationRequest.setSmallestDisplacement(10f)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val newPos = LatLng(
                    locationResult.lastLocation!!.latitude,
                    locationResult.lastLocation!!.longitude
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                if (firstTime) {
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation

                    setRestrictPlacesInCountry(locationResult!!.lastLocation)

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }
                if (previousLocation!!.distanceTo(currentLocation!!) / 1000 <= LIMIT_RANGE)
                    loadAvailableDrivers()
            }
        }
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
        loadAvailableDrivers()
    }

    private fun setRestrictPlacesInCountry(location: Location?) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            var addressList = geoCoder.getFromLocation(location!!.latitude, location.longitude, 1)
            if (addressList!!.size > 0)
                autocompleteSupportFragment.setCountry(addressList[0].countryCode)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadAvailableDrivers() {
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener { location ->
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                var addressList: List<Address> = ArrayList()
                try {
                    addressList =
                        geoCoder.getFromLocation(location.latitude, location.longitude, 1)!!
                    if (addressList.size > 0)
                        cityName = addressList[0].locality

                    if (!TextUtils.isEmpty(cityName)) {
                        val driver_location_ref = FirebaseDatabase.getInstance()
                            .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                            .child(cityName)
                        val gf = GeoFire(driver_location_ref)
                        val geoQuery = gf.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude),
                            distance
                        )
                        geoQuery.removeAllListeners()

                        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++
                                    loadAvailableDrivers()
                                } else {
                                    distance = 0.0
                                    addDriverMarker()
                                }
                            }

                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                                //Common.driversFound.add(DriverGeoModel(key!!, location!!))
                                if(!Common.driversFound.containsKey(key))
                                    Common.driversFound[key!!] = DriverGeoModel(key,location)
                            }

                            override fun onKeyExited(key: String?) {

                            }

                            override fun onKeyMoved(key: String?, location: GeoLocation?) {

                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }

                        })

                        driver_location_ref.addChildEventListener(object : ChildEventListener {
                            override fun onChildAdded(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                                val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                                val geoLocation =
                                    GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])
                                val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance = location.distanceTo(newDriverLocation) / 1000
                                if (newDistance <= LIMIT_RANGE)
                                    findDriverByKey(driverGeoModel)

                            }

                            override fun onChildChanged(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {

                            }

                            override fun onChildRemoved(snapshot: DataSnapshot) {

                            }

                            override fun onChildMoved(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {

                            }

                            override fun onCancelled(error: DatabaseError) {
                                Snackbar.make(requireView(), error.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }

                        })
                    }else{
                        Snackbar.make(requireView(), "city name not found", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    Snackbar.make(requireView(), "Permission Require", Snackbar.LENGTH_SHORT).show()

                }
            }
    }

    private fun addDriverMarker() {
        if (Common.driversFound.size > 0) {
            Observable.fromIterable(Common.driversFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { key: String? ->
                        findDriverByKey(Common.driversFound[key!!])
                    }, { t: Throwable? ->
                        Snackbar.make(requireView(), t!!.message!!, Snackbar.LENGTH_SHORT).show()
                    }
                )
        } else {
            Snackbar.make(requireView(), "Driver Not Found", Snackbar.LENGTH_SHORT).show()
        }

    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        driverGeoModel.driverInfoModel =
                            (snapshot.getValue(DriverInfoModel::class.java))
                        Common.driversFound[driverGeoModel.key!!]!!.driverInfoModel = (snapshot.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    } else {
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found) + driverGeoModel.key)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }
            })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        Dexter.withContext(context)
            .withPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationClickListener {
                        Toast.makeText(context, "Button Clicked", Toast.LENGTH_SHORT).show()
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener { e ->
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng,
                                        18f
                                    )
                                )
                            }
                        true
                    }

                    val view =
                        mapFragment.requireView().findViewById<View>("1".toInt())?.parent as View
                    val locationButton = view.findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(
                        context, "Permission" + p0?.permissionName + " was denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    TODO("Not yet implemented")
                }
            })
            .check()

        mMap.uiSettings.isZoomControlsEnabled = true

        try {
            val success = googleMap.setMapStyle(context?.let {
                MapStyleOptions.loadRawResourceStyle(
                    it, R.raw.uber_maps_style
                )
            })
            if (!success)
                Log.e("Error", "Style parsing error")
        } catch (e: Resources.NotFoundException) {
            e.message?.let { Log.e("Error", it) }
        }


    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        if (!Common.markerList.containsKey(driverGeoModel!!.key))
            Common.markerList.put(
                driverGeoModel!!.key!!,
                mMap.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                driverGeoModel!!.geoLocation!!.latitude,
                                driverGeoModel!!.geoLocation!!.longitude
                            )
                        )
                        .flat(true)
                        .title(
                            Common.buildName(
                                driverGeoModel.driverInfoModel!!.firstName,
                                driverGeoModel.driverInfoModel!!.lastName
                            )
                        )
                        .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                )!!
            )

        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation =
                FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCES)
                    .child(cityName)
                    .child(driverGeoModel!!.key!!)
            driverLocation.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null) {
                            val marker = Common.markerList.get(driverGeoModel!!.key!!)
                            marker!!.remove()
                            Common.markerList.remove(driverGeoModel!!.key!!)
                            driverLocation.removeEventListener(this)
                        }

                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(requireView(), error.message, Snackbar.LENGTH_SHORT).show()
                }

            })
        }
    }
}


