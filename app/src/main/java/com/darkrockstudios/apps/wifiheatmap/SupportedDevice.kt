package com.darkrockstudios.apps.wifiheatmap

import android.app.Activity
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
 * on this device.
 *
 *
 * Sceneform requires Android N on the device as well as OpenGL 3.1 capabilities.
 *
 *
 * Finishes the activity if Sceneform can not run
 */
fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean
{
	val MIN_OPENGL_VERSION = 3.1

	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
	{
		Log.e(ContentValues.TAG, "Sceneform requires Android N or later")
		Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show()
		activity.finish()
		return false
	}
	val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
			.deviceConfigurationInfo
			.glEsVersion
	if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION)
	{
		Log.e(ContentValues.TAG, "Sceneform requires OpenGL ES 3.1 later")
		Toast.makeText(activity, "Sceneform requires OpenGL ES 3.1 or later", Toast.LENGTH_LONG)
				.show()
		activity.finish()
		return false
	}
	return true
}