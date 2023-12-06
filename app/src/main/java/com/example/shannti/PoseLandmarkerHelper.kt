/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.shannti

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
// 별칭을 사용하여 충돌 피하기
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark as MediaPipeNormalizedLandmark
//import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark as ProtoNormalizedLandmark

import kotlin.math.atan2
import kotlin.math.cos
import java.lang.Math
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.math.log
import kotlin.math.abs

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_GPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val poseLandmarkerHelperListener: LandmarkerListener? = null,

    ) {
    // For this example this needs to be a var so it can be reset on changes.
    // If the Pose Landmarker will not change, a lazy val would be preferable.
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    // Return running status of PoseLandmarkerHelper
    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    // Initialize the Pose landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupPoseLandmarker() {
        // Set general pose landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName =
            when (currentModel) {
                MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
                MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
                MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
                else -> "pose_landmarker_full.task"
            }

        baseOptionBuilder.setModelAssetPath(modelName)

        // Check if runningMode is consistent with poseLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (poseLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Pose Landmarker.
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to PoselandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // Run pose landmark using MediaPipe Pose Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // pose landmarker inference on the video. This process will evaluate every
    // frame in the video and attach the results to a bundle that will be
    // returned.
    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the pose landmarker.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        // Note: We need to read width/height from frame instead of getting the width/height
        // of the video directly because MediaRetriever returns frames that are smaller than the
        // actual dimension of the video file.
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height
//        Log.d("YourTag", "Width: $width, Height: $height")

        // If the video is invalid, returns a null detection result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<PoseLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever
                .getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                ?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    // Convert the input Bitmap object to an MPImage object to run inference
                    val mpImage = BitmapImageBuilder(argb8888Frame).build()

                    // Run pose landmarker using MediaPipe Pose Landmarker API
                    poseLandmarker?.detectForVideo(mpImage, timestampMs)
                        ?.let { detectionResult ->
                            resultList.add(detectionResult)
                            val video_landmarks = detectionResult.landmarks()
//                            Log.d("PoseLandmarkerHelper", "Result at timestamp $timestampMs: $video_landmarks")
                            val flattenedvideo = video_landmarks.flatten()
                            val video_result: String = classifyPose(flattenedvideo)
                            Log.d("PoseLandmarkerHelper", "video_Result - calcul: $video_result")




                        } ?: {
                        didErrorOccurred = true
                        poseLandmarkerHelperListener?.onError(
                            "ResultBundle could not be returned" +
                                    " in detectVideoFile"
                        )
                    }
                }
                ?: run {
                    didErrorOccurred = true
                    poseLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be" +
                                " retrieved when detecting in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    // Accepted a Bitmap and runs pose landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run pose landmarker using MediaPipe Pose Landmarker API
        poseLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            val image_landmarks = landmarkResult.landmarks()
            Log.d("PoseLandmarkerHelper", "image: $image_landmarks")
            val flattenedImage = image_landmarks.flatten()
            val image_result: String = classifyPose(flattenedImage)
            Log.d("PoseLandmarkerHelper", "image_Result - calcul: $image_result")

            return ResultBundle(
                listOf(landmarkResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If poseLandmarker?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        poseLandmarkerHelperListener?.onError(
            "Pose Landmarker failed to detect."
        )
        return null
    }


    ////////////////////////////////////////////////////////////////////////////////////////
    // Return the landmark result to this PoseLandmarkerHelper's caller
    private val handler = Handler(Looper.getMainLooper())
    private val processingInterval = 1000L  // 1초에 한 번씩 처리

    private var lastProcessedTime = 0L

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastProcessedTime >= processingInterval) {
            lastProcessedTime = currentTime
            // landmarks에 해당하는 부분 추출
            val landmarks1 = result.landmarks()
            val flattenedLandmarks = landmarks1.flatten()
            val result1: String = classifyPose(flattenedLandmarks)
            Log.d("PoseLandmarkerHelper", "Result1 - calcul: $result1")
            poseLandmarkerHelperListener?.onResults(
                ResultBundle(
                    listOf(result),
                    currentTime - result.timestampMs(),
                    input.height,
                    input.width
                )
            )
            Log.d("PoseLandmarkerHelper", "Result3 - Landmarks: $landmarks1")
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////////
//    계산 부분
    fun calculateAngle(landmark1: MediaPipeNormalizedLandmark, landmark2: MediaPipeNormalizedLandmark, landmark3: MediaPipeNormalizedLandmark): Float {
//        val x1 = landmark1.x() * lastInputWidth
//        val y1 = landmark1.y() * lastInputWidth
//
//        val x2 = landmark2.x() * lastInputHeight
//        val y2 = landmark2.y() * lastInputHeight
//
//        val x3 = landmark3.x() * lastInputWidth
//        val y3 = landmark3.y() * lastInputWidth
        val x1 = landmark1.x() * 1920
        val y1 = landmark1.y() * 1920

        val x2 = landmark2.x() * 1080
        val y2 = landmark2.y() * 1080

        val x3 = landmark3.x() * 1920
        val y3 = landmark3.y() * 1920


        var angle = (Math.toDegrees(atan2((y3 - y2).toDouble(), (x3 - x2).toDouble()) - atan2((y1 - y2).toDouble(), (x1 - x2).toDouble()))).toFloat()

        // 음수 각도를 0에서 360도로 변환
        if (angle < 0) {
            angle += 360f
        }
        return angle
    }


    fun classifyPose(data: List<MediaPipeNormalizedLandmark>): String {
        val angles = mutableMapOf<String, Float>()

        angles["left_elbow"] = calculateAngle(data[11], data[13], data[15])
        angles["right_elbow"] = calculateAngle(data[12], data[14], data[16])
        angles["left_shoulder"] = calculateAngle(data[13], data[11], data[23])
        angles["right_shoulder"] = calculateAngle(data[24], data[12], data[14])
        angles["left_knee"] = calculateAngle(data[23], data[25], data[27])
        angles["right_knee"] = calculateAngle(data[24], data[26], data[28])
        angles["left_hip"] = calculateAngle(data[11], data[23], data[25])
        angles["right_hip"] = calculateAngle(data[12], data[24], data[26])

        // 기준값 설정
        val referenceAngles = mapOf(
            "left_elbow" to 346.23596f,
            "right_elbow" to 347.7879f,
            "left_shoulder" to 29.495209f,
            "right_shoulder" to 335.29987f,
            "left_knee" to 4.310068f,
            "right_knee" to 7.6115246f,
            "left_hip" to 25.393906f,
            "right_hip" to 28.772087f
        )
        val errorRange = 15.0f
        Log.d("PoseLandmarkerHelper", "여기까지 진행상황 ")
        // 각도 비교
        val result = mutableMapOf<String, Float>()
        for ((key, value) in angles) {
            val referenceAngle = referenceAngles[key] ?: continue
            val angleDiff = kotlin.math.abs(value - referenceAngle)

            if (angleDiff <= errorRange) {
                result[key] = value
            }
        }
//        return checkAndReportIssue(result, referenceAngles)
        return when {
            result.size == 8 -> {
                // 모든 각도가 오차 범위 내에 있으면 "perfect"
                Log.d("PoseLandmarkerHelper", "perfect")
                "perfect"
            }

            result.size in 4 until 8 -> {
                // 5개 이상 8개 미만이면 "good"
//                Log.d("PoseLandmarkerHelper", "good")
                val issueMessage = checkAndReportIssue(result, referenceAngles)
//                Log.d("PoseLandmarkerHelper", issueMessage)
                "good: $issueMessage"
            }

            else -> {
                // 그 외의 경우는 "bad"
//                Log.d("PoseLandmarkerHelper", "bad")
                val issueMessage = checkAndReportIssue(result, referenceAngles)
//                Log.d("PoseLandmarkerHelper", issueMessage)
                "bad: $issueMessage"
            }
        }
    }


    fun checkAndReportIssue(result: Map<String, Float>, referenceAngles: Map<String, Float>): String {
        // 차이가 가장 큰 항목 찾기
        val maxDiffItem = result.maxByOrNull { entry ->
            val referenceValue = referenceAngles[entry.key] ?: return@maxByOrNull 0.0f
            kotlin.math.abs(entry.value - referenceValue)
        }
        // 차이가 큰 항목에 대한 메시지 출력
        return if (maxDiffItem != null) {
            val key = maxDiffItem.key
            val anglesValue = maxDiffItem.value
            val referenceValue = referenceAngles[key] ?: 0.0
            "'$key' 부분이 잘못 되었습니다. (기준값: $referenceValue, 측정값: $anglesValue)"
        } else {
            // 특별한 처리가 필요하지 않은 경우 빈 문자열 반환
            "다시 한번 해주세요"
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////
    // Return errors thrown during detection to this PoseLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }


    companion object {
        const val TAG = "PoseLandmarkerHelper"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}