package co.snckbrk.popcorn

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.fragment_record.*
import java.text.SimpleDateFormat
import java.util.*

class RecordFragment : Fragment() {

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var activeRecording: ActiveRecording? = null
    private lateinit var recordingState: VideoRecordEvent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindCamera(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_record, container, false)
    }

    fun bindCamera(cameraProvider : ProcessCameraProvider) {
        var preview : Preview = Preview.Builder()
            .build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
        var cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val qualitySelector = QualitySelector
            .firstTry(QualitySelector.QUALITY_SD)
            .finallyTry(QualitySelector.QUALITY_SD,
                QualitySelector.FALLBACK_STRATEGY_LOWER)

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            // Bind use cases to camera
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                requireParentFragment(),
                cameraSelector,
                videoCapture,
                preview
            )} catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }

       // cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        activeRecording =
            videoCapture.output.prepareRecording(requireActivity(), mediaStoreOutput)
                .withEventListener(
                    mainThreadExecutor,
                    captureListener
                )
                .withAudioEnabled()
                .start()

        Log.i(TAG, "Recording started")
    }

    private val captureListener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status)
            recordingState = event

        updateUI(event)

        if (event is VideoRecordEvent.Finalize) {
            //showVideo(event)
            Log.d("ertert", "hogaya")
            /** replace with share fragment */
        }
    }

    override fun onResume() {
        super.onResume()

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindCamera(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
        initUI()
    }

    fun initUI() {
        record_play.setOnClickListener {
            if (!this::recordingState.isInitialized || recordingState is VideoRecordEvent.Finalize) {
                record_play.isEnabled = false
                record_stop.isEnabled = true
                startRecording()
            }
        }

        record_stop.setOnClickListener {
            record_stop.isEnabled = false
            if (activeRecording == null || recordingState is VideoRecordEvent.Finalize) {
                return@setOnClickListener
            }
            val recording = activeRecording
            if (recording != null) {
                recording.stop()
                activeRecording = null
            }
        }
    }

    private fun updateUI(event: VideoRecordEvent) {
        val state = if (event is VideoRecordEvent.Status) recordingState.getName()
        else event.getName()
        when (event) {
            is VideoRecordEvent.Status -> {
                // placeholder: we update the UI with new status after this when() block,
                // nothing needs to do here.
            }
            is VideoRecordEvent.Start -> {
                record_play.isEnabled = false
                record_stop.isEnabled = true
            }
            is VideoRecordEvent.Finalize-> {
                record_play.isEnabled = true
                record_stop.isEnabled = false
                //add share btn
            }
            is VideoRecordEvent.Pause -> {
            }
            is VideoRecordEvent.Resume -> {
            }
            else -> {
                Log.e(TAG, "Error(Unknown Event) from Recorder")
                return
            }
        }
    }
}

fun VideoRecordEvent.getName() : String {
    return when (this) {
        is VideoRecordEvent.Status -> "Status"
        is VideoRecordEvent.Start -> "Started"
        is VideoRecordEvent.Finalize-> "Finalized"
        is VideoRecordEvent.Pause -> "Paused"
        is VideoRecordEvent.Resume -> "Resumed"
        else -> "Error(Unknown)"
    }
}