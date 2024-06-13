package com.example.ridersapp.Common

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.widget.TextView
import com.example.ridersapp.Model.DriverGeoModel
import com.example.ridersapp.Model.RiderModel
import com.google.android.gms.maps.model.Marker
import java.util.Calendar

object Common {
    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome,")
            .append(currentRider!!.firstName)
            .append(" ")
            .append(currentRider!!.lastName)
            .toString()

    }

    fun buildName(firstName: String?, lastName: String?): String? {
        return java.lang.StringBuilder(firstName!!).append(" ").append(lastName).toString()
    }

    fun setWelcomeMessage(txtWelcome: TextView) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 1 && hour <= 12)
            txtWelcome.setText(java.lang.StringBuilder("Good Morning!!"))
        else if (hour > 12 && hour <= 17)
            txtWelcome.setText(java.lang.StringBuilder("Good Afternoon!!"))
        else
            txtWelcome.setText(java.lang.StringBuilder("Good Evening!!"))
    }

    fun valueAnimate(duration: Int, listener: AnimatorUpdateListener): ValueAnimator {
        val va = ValueAnimator.ofFloat(0f, 100f)
        va.duration = duration.toLong()
        va.addUpdateListener(listener)
        va.repeatCount = ValueAnimator.INFINITE
        va.repeatCount = ValueAnimator.RESTART
        va.start()
        return va
    }

    val markerList: MutableMap<String, Marker> = HashMap<String, Marker>()
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    val driversFound: MutableMap<String,DriverGeoModel> = HashMap<String,DriverGeoModel>()
    val DRIVERS_LOCATION_REFERENCES: String = "DriversLocation"
    var currentRider: RiderModel? = null
    val RIDER_INFO_REFERENCE: String = "Riders"
}