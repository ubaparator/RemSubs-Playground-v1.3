package com.example.encode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by Termux RUN_COMMAND PendingIntent upon command execution completion.
 */
class TermuxCommandResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra("jobId") ?: ""
        val exitCode = intent.getIntExtra("exitCode", intent.getIntExtra("result_code", -1))
        val errCode = intent.getIntExtra("errCode", 0)
        val errmsg = intent.getStringExtra("errmsg")
        val stdout = intent.getStringExtra("stdout") ?: ""
        val stderr = intent.getStringExtra("stderr") ?: ""

        Log.i(
            TAG,
            "Termux command finished. JobId: $jobId, exitCode: $exitCode, errCode: $errCode, errmsg: $errmsg"
        )

        TermuxEncodeManager.onCommandResult(
            jobId = jobId,
            exitCode = exitCode,
            errCode = errCode,
            errmsg = errmsg,
            stdout = stdout,
            stderr = stderr
        )
    }

    companion object {
        private const val TAG = "TermuxResultReceiver"
    }
}
