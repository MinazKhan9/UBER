package com.example.ridersapp

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.ridersapp.Common.Common
import com.example.ridersapp.Model.EventBus.SelectPlaceEvent
import com.example.ridersapp.Utils.UserUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.ui.IconGenerator
import io.reactivex.disposables.CompositeDisposable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    var animator: ValueAnimator? = null
    private val DESIRED_NUM_OF_SPINS = 5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN = 40

    var lastUserCircle: Circle? = null
    val duration = 1000
    var lastPulseAnimator: ValueAnimator? = null

    private lateinit var mMap: GoogleMap
    private lateinit var txt_origin: TextView

    private var selectedPlaceEvent: SelectPlaceEvent? = null

    private lateinit var mapFragment: SupportMapFragment

    private val compositeDisposable = CompositeDisposable()
    private var blackPolyLine: Polyline? = null
    private var greyPolyLine: Polyline? = null
    private var polylineOptions: PolylineOptions? = null
    private var blackPolylineOptions: PolylineOptions? = null
    private var polylineList: ArrayList<LatLng>? = null

    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null

    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
        super.onStart()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectPlaceEvent::class.java))
            EventBus.getDefault().removeStickyEvent(SelectPlaceEvent::class.java)
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSelectPlaceEvent(event: SelectPlaceEvent) {
        selectedPlaceEvent = event
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_driver)

        init()

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun init() {
        val ublayout = findViewById<CardView>(R.id.confirm_uber_layout)
        val pklayout = findViewById<CardView>(R.id.confirm_pickup_layout)
        val ubbtn = findViewById<Button>(R.id.btn_confirm_uber)
        val pkbtn = findViewById<Button>(R.id.btn_confirm_pickup)
        ubbtn.setOnClickListener {
            pklayout.visibility = View.VISIBLE
            ublayout.visibility = View.GONE

            setDataPickup()
        }
        pkbtn.setOnClickListener {
            if (mMap == null) return@setOnClickListener
            if (selectedPlaceEvent == null) return@setOnClickListener

            mMap.clear()

            val cameraPos = CameraPosition.Builder().target(selectedPlaceEvent!!.origin)
                .tilt(45f)
                .zoom(16f)
                .build()
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

            addMarkerWithPulseAnimation()
        }


    }

    private fun addMarkerWithPulseAnimation() {
        val fdlayout = findViewById<CardView>(R.id.finding_your_ride_layout)
        val pklayout = findViewById<CardView>(R.id.confirm_pickup_layout)
        val fmlayout = findViewById<View>(R.id.fill_maps)
        pklayout.visibility = View.GONE
        fmlayout.visibility = View.VISIBLE
        fdlayout.visibility = View.VISIBLE

        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
                .position(selectedPlaceEvent!!.origin)
        )

        addPulsatingEffect(selectedPlaceEvent!!.origin)
    }

    private fun addPulsatingEffect(origin: LatLng) {
        if (lastPulseAnimator != null) lastPulseAnimator!!.cancel()
        if (lastUserCircle != null) lastUserCircle!!.center = origin
        lastPulseAnimator =
            Common.valueAnimate(duration, object : ValueAnimator.AnimatorUpdateListener {
                override fun onAnimationUpdate(animation: ValueAnimator) {
                    if (lastUserCircle != null) lastUserCircle!!.radius =
                        animation!!.animatedValue.toString().toDouble() else {
                        lastUserCircle = mMap.addCircle(
                            CircleOptions()
                                .center(origin)
                                .radius(animation!!.animatedValue.toString().toDouble())
                                .strokeColor(Color.WHITE)
                                .fillColor(
                                    ContextCompat.getColor(
                                        this@RequestDriverActivity,
                                        R.color.map_darker
                                    )
                                )
                        )
                    }
                }
            })

        startMapCameraSpinningAnimation(mMap.cameraPosition.target)
    }

    private fun startMapCameraSpinningAnimation(target: LatLng) {
        if (animator != null) animator!!.cancel()
        animator = ValueAnimator.ofFloat(0F, (DESIRED_NUM_OF_SPINS * 360).toFloat())
        animator!!.duration =
            (DESIRED_NUM_OF_SPINS * DESIRED_SECONDS_PER_ONE_FULL_360_SPIN * 1000).toLong()
        animator!!.interpolator = LinearInterpolator()
        animator!!.startDelay = (100)
        animator!!.addUpdateListener { valueAnimator ->
            val newBearingValue = valueAnimator.animatedValue as Float
            mMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(16f)
                        .tilt(45f)
                        .bearing(newBearingValue)
                        .build()
                )
            )
        }
        animator!!.start()

        findNearbyDriver(target)

    }

    private fun findNearbyDriver(target: LatLng) {
        if (Common.driversFound.size > 0) {
            var min = 0f
            var foundDriver = Common.driversFound[Common.driversFound.keys.iterator().next()]
            val currentRiderLocation = Location("")
            currentRiderLocation.latitude = target!!.latitude
            currentRiderLocation.longitude = target!!.longitude

            for (key in Common.driversFound.keys) {
                val driverLocation = Location("")
                driverLocation.latitude = Common.driversFound[key]!!.geoLocation!!.latitude
                driverLocation.longitude = Common.driversFound[key]!!.geoLocation!!.longitude

                if (min == 0f) {
                    min = driverLocation.distanceTo(currentRiderLocation)
                    foundDriver = Common.driversFound[key]
                } else if (driverLocation.distanceTo(currentRiderLocation) < min) {
                    min = driverLocation.distanceTo(currentRiderLocation)
                    foundDriver = Common.driversFound[key]
                }
            }

            val main = findViewById<RelativeLayout>(R.id.main_layout)
            Snackbar.make(
                main, StringBuilder("Found driver: ")
                    .append(foundDriver!!.driverInfoModel!!.phoneNumber), Snackbar.LENGTH_LONG
            ).show()

           // UserUtils.sendRequestToDriver(this@RequestDriverActivity,main,foundDriver,target)
        } else {
            val main = findViewById<RelativeLayout>(R.id.main_layout)
            Snackbar.make(main, "Driver Not Found", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (animator != null) animator!!.end()
        super.onDestroy()
    }

    private fun setDataPickup() {
        val address = findViewById<TextView>(R.id.txt_address_pickup)
        address.text = if (txt_origin != null) txt_origin.text else "None"
        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {
        val view = layoutInflater.inflate(R.layout.pickup_info_windows, null)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin)
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this,
                    R.raw.uber_maps_style
                )
            )
            if (!success)
                Snackbar.make(
                    mapFragment.requireView(),
                    "Load map style failed",
                    Snackbar.LENGTH_LONG
                ).show()
        } catch (e: Exception) {
            Snackbar.make(mapFragment.requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
        }
    }

}