package com.example.cowall.utilities

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast

fun Context.displayToast(message: String) {
    Log.d("cowall",message)
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun View.show(){
    this.visibility = View.VISIBLE
}

fun printLog(message: String){
    Log.d("Walld", message)
}