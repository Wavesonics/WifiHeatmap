package com.darkrockstudios.apps.wifiheatmap

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.support.v4.graphics.ColorUtils
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.CompletableFuture


data class Reading(var strength: Int, var node: TransformableNode?)

class MainActivity : AppCompatActivity()
{
	private var materialColors: MutableList<Material> = mutableListOf()

	private val heatMap = SparseArray<SparseArray<Reading>>()

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
		val start = 0xFFFF0000.toInt()
		val end = 0xFF00FF00.toInt()

		val colors = mutableListOf<CompletableFuture<Material>>()
		val max = 20
		for (ii in 0..max)
		{
			val colorInt = ColorUtils.blendARGB(start, end, (ii.toFloat() / (max - 1).toFloat()))

			Log.d("adam", colorInt.toString(16))

			val color = Color()
			color.set(colorInt)

			val materialFuture = MaterialFactory.makeOpaqueWithColor(this, color)
			colors.add(materialFuture)
		}

		CompletableFuture.allOf(*colors.toTypedArray())
				.handle { _, _ ->

					colors.forEach {
						materialColors.add(it.get())
					}
				}
	}

	private fun setReading(x: Float, z: Float, strength: Int): Boolean
	{
		val intX = (x * 10f).toInt()
		val intZ = (z * 10f).toInt()

		var col = heatMap[intZ]
		if (col == null)
		{
			heatMap.put(intZ, SparseArray())
			col = heatMap[intZ]
		}

		val reading = col[intX]
		val existingRenderable: TransformableNode? = reading?.node

		return if (reading == null || reading.strength != strength)
		{
			existingRenderable?.setParent(null)

			val renderable = createReadingRenderable(x, z, strength)

			col.put(intX, Reading(strength, renderable))
			true
		}
		else
		{
			false
		}
	}

	/*
		private fun getReading(x: Int, y: Int): Int
		{
			val col = heatMap[y]
			return if(col != null)
			{
				col[x] ?: 0
			}
			else
			{
				0
			}
		}
	*/
	private fun wifiManager() = getSystemService(Context.WIFI_SERVICE) as WifiManager

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

	private fun createBlock(level: Int): ModelRenderable?
	{
		//val scaleFactor = WifiManager.calculateSignalLevel(level, 100)
		val normalizedLevel = (level * -1) - 50
		val scaleFactor = 1f - (normalizedLevel.toFloat() / 50f)

		//Log.d("adam", "scaleFactor: $s level: $level")

		val height = 1f * scaleFactor

		val size = 0.5f

		val material = getColor(scaleFactor)

		return ShapeFactory.makeCube(Vector3(0.25f, height, 0.25f),
				Vector3(0f, size, 0f),
				material)
	}

	fun logscale(value: Float): Float
	{
		return if (value > 1.0f)
			Math.log(value.toDouble()).toFloat()
		else
			value - 1.0f
	}

	private fun getColor(ratio: Float): Material
	{
		val colorIndex = ((materialColors.size - 1) * ratio).toInt()
		Log.d("adam", "colorIndex: $colorIndex ratio: $ratio")
		return materialColors[colorIndex]
	}

	private var anchorNode: AnchorNode? = null
	private var rootNode: Node? = null

	private fun onTapArPlaneListener(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent)
	{
		val ar = arFragment ?: return

		if (ar.arSceneView.arFrame.camera.trackingState === TrackingState.TRACKING)
		{
			val trackable = hitResult.trackable
			if (trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose))
			{
				// Create the Anchor
				if (anchorNode == null)
				{
					val anchor = hitResult.createAnchor()
					anchorNode = AnchorNode(anchor)
					anchorNode?.setParent(ar.arSceneView.scene)

					rootNode = Node()
					rootNode?.setParent(anchorNode)
				}
			}
		}
	}

	private fun createReadingRenderable(x: Float, z: Float, strength: Int): TransformableNode?
	{
		val ar = arFragment ?: return null
		rootNode ?: return null

		// Create the transformable sphere and add it to the anchor
		//val base = Node()
		val node = TransformableNode(ar.transformationSystem)
		node.setParent(rootNode)

		val posX = x
		val posY = 0f
		val posZ = z

		//sphere.localPosition = sphere.worldToLocalPoint(Vector3(posX, posY, posZ))
		node.localPosition = Vector3(posX, posY, posZ)
		node.renderable = createBlock(strength)

		return node
	}

	private fun getGridPosition(): Vector3
	{
		val ar = arFragment ?: return Vector3()

		val cameraPos = ar.arSceneView.scene.camera.worldPosition

		var x = roundToHalf(cameraPos.x.toDouble())
		//x = if(x.toInt() % 2 == 0) x else x - 1

		var z = roundToHalf(cameraPos.z.toDouble())
		//z = if(z.toInt() % 2 == 0) z else z - 1

		return Vector3(x.toFloat(), 10f, z.toFloat())
	}

	fun roundToHalf(d: Double): Double = Math.round(d * 2) / 2.0

	private fun getSignalStrength(): Int
	{
		val wifiInfo = wifiManager().connectionInfo
		return wifiInfo.rssi
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

			val gridPosition = getGridPosition()
			setReading(gridPosition.x, gridPosition.z, strength)

			val ar = arFragment ?: return

			wifi_ssid_strength.text = getString(R.string.signal_strength, strength)

			//val pos = ar.arSceneView.scene.camera.worldPosition
			//wifi_ssid_strength.text = "( ${pos.x}, ${pos.y}, ${pos.z} )"
		}
		else
		{
			wifi_ssid_strength.text = ""
		}

		if (wifiManager().connectionInfo != null && rootNode != null)
		{
			wifi_found_view.visibility = View.VISIBLE
		}
		else
		{
			wifi_found_view.visibility = View.GONE
		}
	}

	companion object
	{
		const val TAG = "main"
	}

	private val wifi: WifiManager
		get() = getSystemService(Context.WIFI_SERVICE) as WifiManager
}
