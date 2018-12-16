// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.samples.apps.mlkit.kotlin

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import com.google.android.gms.common.annotation.KeepName
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.samples.apps.mlkit.R
import com.google.firebase.samples.apps.mlkit.common.CameraSource
import com.google.firebase.samples.apps.mlkit.common.CameraSourcePreview
import com.google.firebase.samples.apps.mlkit.common.GraphicOverlay
import com.google.firebase.samples.apps.mlkit.kotlin.barcodescanning.BarcodeScanningProcessor
import com.google.firebase.samples.apps.mlkit.kotlin.custommodel.CustomImageClassifierProcessor
import com.google.firebase.samples.apps.mlkit.kotlin.facedetection.FaceContourDetectorProcessor
import com.google.firebase.samples.apps.mlkit.kotlin.facedetection.FaceDetectionProcessor
import com.google.firebase.samples.apps.mlkit.kotlin.imagelabeling.ImageLabelingProcessor
import com.google.firebase.samples.apps.mlkit.kotlin.textrecognition.TextRecognitionProcessor
import kotlinx.android.synthetic.main.activity_live_preview.*
import java.io.IOException

/** Demo app showing the various features of ML Kit for Firebase. This class is used to
 * set up continuous frame processing on frames from a camera source.  */
@KeepName
class LivePreviewActivity : AppCompatActivity(), OnRequestPermissionsResultCallback,
        OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var selectedModel = FACE_CONTOUR

    private val requiredPermissions: Array<String?>
        get() {
            return try {
                val info = this.packageManager
                        .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
                val ps = info.requestedPermissions
                if (ps != null && ps.isNotEmpty()) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
            } catch (e: Exception) {
                arrayOfNulls(0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContentView(R.layout.activity_live_preview)

        preview = firePreview
        if (preview == null) {
            Log.d(TAG, "Preview is null")
        }

        graphicOverlay = fireFaceOverlay
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null")
        }

        val spinner = spinner
        val options = arrayListOf(FACE_CONTOUR,
                FACE_DETECTION,
                BARCODE_DETECTION,
                IMAGE_LABEL_DETECTION,
                CLASSIFICATION_QUANT,
                CLASSIFICATION_FLOAT)
        // Creating adapter for spinner
        val dataAdapter = ArrayAdapter(this, R.layout.spinner_style, options)
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // attaching data adapter to spinner
        spinner.adapter = dataAdapter
        spinner.onItemSelectedListener = this

        val facingSwitch = facingSwitch
        facingSwitch.setOnCheckedChangeListener(this)
        // Hide the toggle button if there is only 1 camera
        if (Camera.getNumberOfCameras() == 1) {
            facingSwitch.visibility = View.GONE
        }

        if (allPermissionsGranted()) {
            createCameraSource(selectedModel)
        } else {
            getRuntimePermissions()
        }
    }

    @Synchronized
    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        selectedModel = parent.getItemAtPosition(pos).toString()
        Log.d(TAG, "Selected model: $selectedModel")
        preview?.stop()
        if (allPermissionsGranted()) {
            createCameraSource(selectedModel)
            startCameraSource()
        } else {
            getRuntimePermissions()
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Do nothing.
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        Log.d(TAG, "Set facing")

        cameraSource?.let {
            if (isChecked) {
                it.setFacing(CameraSource.CAMERA_FACING_FRONT)
            } else {
                it.setFacing(CameraSource.CAMERA_FACING_BACK)
            }
        }
        preview?.stop()
        startCameraSource()
    }

    private fun createCameraSource(model: String) {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(this, graphicOverlay)
        }

        try {
            when (model) {
                CLASSIFICATION_QUANT -> {
                    Log.i(TAG, "Using Custom Image Classifier (quant) Processor")
                    cameraSource?.setMachineLearningFrameProcessor(CustomImageClassifierProcessor(this, true))
                }
                CLASSIFICATION_FLOAT -> {
                    Log.i(TAG, "Using Custom Image Classifier (float) Processor")
                    cameraSource?.setMachineLearningFrameProcessor(CustomImageClassifierProcessor(this, false))
                }
                TEXT_DETECTION -> {
                    Log.i(TAG, "Using Text Detector Processor")
                    cameraSource?.setMachineLearningFrameProcessor(TextRecognitionProcessor())
                }
                FACE_DETECTION -> {
                    Log.i(TAG, "Using Face Detector Processor")
                    cameraSource?.setMachineLearningFrameProcessor(FaceDetectionProcessor())
                }
                BARCODE_DETECTION -> {
                    Log.i(TAG, "Using Barcode Detector Processor")
                    cameraSource?.setMachineLearningFrameProcessor(BarcodeScanningProcessor())
                }
                IMAGE_LABEL_DETECTION -> {
                    Log.i(TAG, "Using Image Label Detector Processor")
                    cameraSource?.setMachineLearningFrameProcessor(ImageLabelingProcessor())
                }
                FACE_CONTOUR -> {
                    Log.i(TAG, "Using Face Contour Detector Processor")
                    cameraSource?.setMachineLearningFrameProcessor(FaceContourDetectorProcessor())
                }
                else -> Log.e(TAG, "Unknown model: $model")
            }
        } catch (e: FirebaseMLException) {
            Log.e(TAG, "can not create camera source: $model")
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private fun startCameraSource() {
        cameraSource?.let {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null")
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null")
                }
                preview?.start(cameraSource, graphicOverlay)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                cameraSource?.release()
                cameraSource = null
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        startCameraSource()
    }

    /** Stops the camera.  */
    override fun onPause() {
        super.onPause()
        preview?.stop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!)) {
                return false
            }
        }
        return true
    }

    private fun getRuntimePermissions() {
        val allNeededPermissions = arrayListOf<String>()
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!)) {
                allNeededPermissions.add(permission)
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "Permission granted!")
        if (allPermissionsGranted()) {
            createCameraSource(selectedModel)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val FACE_DETECTION = "Face Detection"
        private const val TEXT_DETECTION = "Text Detection"
        private const val BARCODE_DETECTION = "Barcode Detection"
        private const val IMAGE_LABEL_DETECTION = "Label Detection"
        private const val CLASSIFICATION_QUANT = "Classification (quantized)"
        private const val CLASSIFICATION_FLOAT = "Classification (float)"
        private const val FACE_CONTOUR = "Face Contour"
        private const val TAG = "LivePreviewActivity"
        private const val PERMISSION_REQUESTS = 1

        private fun isPermissionGranted(context: Context, permission: String): Boolean {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission granted: $permission")
                return true
            }
            Log.i(TAG, "Permission NOT granted: $permission")
            return false
        }
    }
}
