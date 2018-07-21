package com.darkrockstudios.apps.wifiheatmap

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.view.View
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.CompletableFuture


class MainActivity : AppCompatActivity()
{
	private var materialColors: MutableList<Material> = mutableListOf()

	private var arFragment: ArFragment? = null

	private val handler = Handler()
	private val viewUpdateTask = Runnable {
		updateAndSchedule()
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		if (!checkIsSupportedDeviceOrFinish(this))
		{
			return
		}

		loadData()

		setContentView(R.layout.activity_main)

		arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment
		arFragment?.setOnTapArPlaneListener(this::onTapArPlaneListener)
	}

	private fun loadData()
	{
		val level_0_Completable = MaterialFactory.makeOpaqueWithColor(this, Color(1.0f, 0.0f, 0.0f))
		val level_1_Completable = MaterialFactory.makeOpaqueWithColor(this, Color(1.0f, 0.1f, 0.1f))
		val level_2_Completable = MaterialFactory.makeOpaqueWithColor(this, Color(1.0f, 0.2f, 0.15f))
		val level_3_Completable = MaterialFactory.makeOpaqueWithColor(this, Color(0.75f, 1.0f, 0.25f))
		val level_4_Completable = MaterialFactory.makeOpaqueWithColor(this, Color(0.0f, 1.0f, 0.25f))

		CompletableFuture.allOf(level_0_Completable,
								level_1_Completable,
								level_2_Completable,
								level_3_Completable,
								level_4_Completable)
				.handle { notUsed, throwable ->
					materialColors.add(level_0_Completable.get())
					materialColors.add(level_1_Completable.get())
					materialColors.add(level_2_Completable.get())
					materialColors.add(level_3_Completable.get())
					materialColors.add(level_4_Completable.get())
				}
	}

	private fun wifiManager() = getSystemService(Context.WIFI_SERVICE) as WifiManager ?: throw IllegalStateException("Could not get WifiManager")

	override fun onStart()
	{
		super.onStart()

		updateAndSchedule()
	}

	override fun onStop()
	{
		super.onStop()

		handler.removeCallbacks(viewUpdateTask)
	}

	private fun createSphere( level: Int ): ModelRenderable?
	{
		return when(level)
		{
			0 -> ShapeFactory.makeSphere(0.01f, Vector3(), materialColors[0])
			1 -> ShapeFactory.makeSphere(0.02f, Vector3(), materialColors[1])
			2 -> ShapeFactory.makeSphere(0.03f, Vector3(), materialColors[2])
			3 -> ShapeFactory.makeSphere(0.055f, Vector3(), materialColors[3])
			else -> ShapeFactory.makeSphere(0.075f, Vector3(), materialColors[4])
		}
	}

	private fun onTapArPlaneListener(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent)
	{
		val ar = arFragment ?: return

		// Create the Anchor
		val anchor = hitResult.createAnchor()
		val anchorNode = AnchorNode(anchor)
		anchorNode.setParent(ar.arSceneView.scene)

		// Create the transformable sphere and add it to the anchor
		val sphere = TransformableNode(ar.transformationSystem)
		sphere.setParent(anchorNode)

		val cameraPos = ar.arSceneView.scene.camera.worldPosition

		val df = DecimalFormat("#.#")
		df.roundingMode = RoundingMode.FLOOR

		val posX = df.format(cameraPos.x).toFloat()
		val posY = df.format(cameraPos.y).toFloat()
		val posZ = df.format(cameraPos.z).toFloat()

		sphere.localPosition = sphere.worldToLocalPoint(Vector3(posX, posY, posZ))
		sphere.renderable = createSphere( getSignalStrength() )
	}

	private fun getSignalStrength(): Int
	{
		val numberOfLevels = 5
		val wifiInfo = wifiManager().connectionInfo
		return WifiManager.calculateSignalLevel(wifiInfo.rssi, numberOfLevels)
	}

	private fun updateAndSchedule()
	{
		updateViews()
		handler.postDelayed(viewUpdateTask, 250)
	}

	private fun updateViews()
	{
		if (wifiManager().connectionInfo != null)
		{
			val strength = getSignalStrength()

			val ar = arFragment ?: return

			wifi_ssid_strength.text = getString(R.string.signal_strength, strength)

			//val pos = ar.arSceneView.scene.camera.worldPosition
			//wifi_ssid_strength.text = "( ${pos.x}, ${pos.y}, ${pos.z} )"

			wifi_found_view.visibility = View.VISIBLE
		}
		else
		{
			wifi_ssid_strength.text = ""
			wifi_found_view.visibility = View.GONE
		}
	}
}
