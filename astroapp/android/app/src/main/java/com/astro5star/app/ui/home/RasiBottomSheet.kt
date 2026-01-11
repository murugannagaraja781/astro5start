package com.astro5star.app.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.astro5star.app.R
import com.astro5star.app.data.model.RasiData

class RasiBottomSheet(private val rasiData: RasiData) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_rasi, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvRasiTitle)
        val tvPrediction = view.findViewById<TextView>(R.id.tvRasiPrediction)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        tvTitle.text = "${rasiData.name_tamil} - பலன்கள்"
        tvPrediction.text = rasiData.prediction

        btnClose.setOnClickListener {
            dismiss()
        }
    }
}
