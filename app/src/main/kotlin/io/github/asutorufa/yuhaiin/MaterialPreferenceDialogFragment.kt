package io.github.asutorufa.yuhaiin

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.preference.DialogPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class MaterialPreferenceDialogFragment : DialogInterface.OnClickListener {
    fun onCreateDialog(
        activity: FragmentActivity,
        preference: DialogPreference,
        onCreateDialogView: (context: Context) -> View?,
        onBindDialogView: (view: View) -> Unit,
        onPrepareDialogBuilder: (builder: AlertDialog.Builder) -> Unit,
    ): Dialog {
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(preference.dialogTitle)
            setIcon(preference.dialogIcon)
            setPositiveButton(preference.positiveButtonText, this@MaterialPreferenceDialogFragment)
            setNegativeButton(preference.negativeButtonText, this@MaterialPreferenceDialogFragment)

            onCreateDialogView(activity)?.let {
                onBindDialogView(it)
                setView(it)
            } ?: setMessage(preference.dialogMessage)

            onPrepareDialogBuilder(this)

            //if (needInputMethod()) {
            //    requestInputMethod(dialog)
            //}

            return create()
        }
    }

/* Override the methods that access mWhichButtonClicked (because we cannot set it properly here) */

    /**
     * Which button was clicked.
     */
    private var mWhichButtonClicked = 0

    override fun onClick(dialog: DialogInterface, which: Int) {
        mWhichButtonClicked = which
    }

    fun onDismiss() {
        onDialogClosedWasCalledFromOnDismiss = true
    }

    private var onDialogClosedWasCalledFromOnDismiss = false

    fun onDialogClosed(positiveResult: Boolean): Boolean {
        return if (onDialogClosedWasCalledFromOnDismiss) {
            onDialogClosedWasCalledFromOnDismiss = false
            // this means the positiveResult needs to be calculated from our mWhichButtonClicked
            mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE
        } else positiveResult
    }
}