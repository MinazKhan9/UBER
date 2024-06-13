package com.example.ridersapp.Utils

import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import com.example.ridersapp.Common.Common
import com.example.ridersapp.Model.DriverGeoModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object UserUtils {
    fun updateUser(view: View?, updateData: HashMap<String, Any>) {
        FirebaseDatabase.getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e ->
                Snackbar.make(view!!,e.message!!, Snackbar.LENGTH_LONG).show()
            }
            .addOnSuccessListener {
                Snackbar.make(view!!,"Updated information successfully", Snackbar.LENGTH_LONG).show()
            }

    }

    fun sendRequestToDriver(
        context: Context,
        main: RelativeLayout?,
        foundDriver: DriverGeoModel?,
        target: LatLng
    ) {

    }

}