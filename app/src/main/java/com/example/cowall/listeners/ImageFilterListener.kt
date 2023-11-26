package com.example.cowall.listeners

import com.example.cowall.data.ImageFilter

interface ImageFilterListener {
    fun onFilterSelected( imageFilter: ImageFilter )
}