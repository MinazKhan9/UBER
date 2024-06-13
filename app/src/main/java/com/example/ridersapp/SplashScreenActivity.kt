package com.example.ridersapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ridersapp.Common.Common
import com.example.ridersapp.Model.RiderModel
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.Arrays
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {
    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    private lateinit var provider: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var database: FirebaseDatabase
    private lateinit var riderInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe({
            firebaseAuth.addAuthStateListener(listener)
        })
    }

    override fun onStop() {
        if (firebaseAuth != null && listener != null) firebaseAuth.removeAuthStateListener(listener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        init()
    }

    private fun init() {
        database = FirebaseDatabase.getInstance()
        riderInfoRef = database.getReference(Common.RIDER_INFO_REFERENCE)
        provider = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                checkUserFromFirebase()
            } else
                showLoginLayout()
        }
    }

    private fun checkUserFromFirebase() {
        riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG)
                        .show()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Toast.makeText(this@SplashScreenActivity, "User already exists", Toast.LENGTH_LONG).show()
                        val model = snapshot.getValue(RiderModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }
            })
    }

    private fun goToHomeActivity(model: RiderModel?) {
        Common.currentRider = model
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.phone_button_sign_in)
            .setGoogleButtonId(R.id.google_button_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.Login_Theme)
                .setAvailableProviders(provider)
                .setIsSmartLockEnabled(false)
                .build(), LOGIN_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "" + response!!.error!!.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.Dialog_Theme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val phoneNumber = itemView.findViewById<View>(R.id.phone_number) as TextInputEditText
        val firstName = itemView.findViewById<View>(R.id.first_name) as TextInputEditText
        val lastName = itemView.findViewById<View>(R.id.last_name) as TextInputEditText

        val btnContinue = itemView.findViewById<Button>(R.id.btn_continue) as Button

        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null &&
            !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        )
            phoneNumber.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        btnContinue.setOnClickListener {
            if (TextUtils.isDigitsOnly(firstName.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please Enter First Name",
                    Toast.LENGTH_LONG
                )
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(lastName.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please Enter Last Name",
                    Toast.LENGTH_LONG
                )
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(phoneNumber.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please Enter Phone Number",
                    Toast.LENGTH_LONG
                )
                return@setOnClickListener
            } else {
                val model = RiderModel()
                model.firstName = firstName.text.toString()
                model.lastName = lastName.text.toString()
                model.phoneNumber = phoneNumber.text.toString()
                model.rating = 0.0

                riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "" + it.message,
                            Toast.LENGTH_LONG
                        )
                        dialog.dismiss()
                        //progress_bar.visibility = View.GONE
                    }
                    .addOnSuccessListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "Registered Successfully",
                            Toast.LENGTH_LONG
                        )
                        dialog.dismiss()

                        goToHomeActivity(model)
                        //progress_bar.visibility = View.GONE
                    }
            }
        }
    }

}
