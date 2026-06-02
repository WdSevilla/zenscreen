package com.freekiosk.app

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.freekiosk.app.databinding.DialogPinBinding

class PinDialogFragment : DialogFragment() {

    var onSuccess: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPinBinding.inflate(layoutInflater)

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val entered = binding.pinInput.text?.toString() ?: ""
                val stored = Prefs.getPin(requireContext())
                if (entered == stored) {
                    onSuccess?.invoke()
                } else {
                    Toast.makeText(requireContext(), R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }

    companion object {
        const val TAG = "PinDialog"
        fun newInstance(onSuccess: () -> Unit) =
            PinDialogFragment().also { it.onSuccess = onSuccess }
    }
}
