/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package org.tensorflow.lite.examples.poseestimation.ml

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.examples.poseestimation.MainActivity
import org.tensorflow.lite.examples.poseestimation.data.*
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.examples.poseestimation.camera.CameraSource
import java.util.*
import kotlin.math.*


private var rightAlbowAngle1 = ArrayList<Float>()
private var rightAlbowAngle2 = ArrayList<Float>()
private var rightAlbowAngle3 = ArrayList<Float>()
private var rightAlbowAngle4 = ArrayList<Float>()
private var rightAlbowAngle5 = ArrayList<Float>()
private var rightAlbowAngle6 = ArrayList<Float>()

private var rightShoulderAngle1 = ArrayList<Float>()
private var rightShoulderAngle2 = ArrayList<Float>()
private var rightShoulderAngle3 = ArrayList<Float>()
private var rightShoulderAngle4 = ArrayList<Float>()
private var rightShoulderAngle5 = ArrayList<Float>()
private var rightShoulderAngle6 = ArrayList<Float>()

private var rightHipAngle1 = ArrayList<Float>()
private var rightHipAngle2 = ArrayList<Float>()
private var rightHipAngle3 = ArrayList<Float>()
private var rightHipAngle4 = ArrayList<Float>()
private var rightHipAngle5 = ArrayList<Float>()
private var rightHipAngle6 = ArrayList<Float>()

private var rightKneeAngle1 = ArrayList<Float>()
private var rightKneeAngle2 = ArrayList<Float>()
private var rightKneeAngle3 = ArrayList<Float>()
private var rightKneeAngle4 = ArrayList<Float>()
private var rightKneeAngle5 = ArrayList<Float>()
private var rightKneeAngle6 = ArrayList<Float>()

private var leftKneeAngle1 = ArrayList<Float>()
private var leftKneeAngle2 = ArrayList<Float>()
private var leftKneeAngle3 = ArrayList<Float>()
private var leftKneeAngle4 = ArrayList<Float>()
private var leftKneeAngle5 = ArrayList<Float>()

private var rightAlbowAngles = arrayOf(rightAlbowAngle1, rightAlbowAngle2, rightAlbowAngle3, rightAlbowAngle4,
    rightAlbowAngle5, rightAlbowAngle6)
//private var rightAlbowAngles = arrayOf(rightAlbowAngle1.toFloatArray(), rightAlbowAngle2.toFloatArray(), rightAlbowAngle3.toFloatArray(), rightAlbowAngle4.toFloatArray(),
//    rightAlbowAngle5.toFloatArray(), rightAlbowAngle6.toFloatArray())
private var rightShoulderAngles = arrayOf(rightShoulderAngle1, rightShoulderAngle2, rightShoulderAngle3,
    rightShoulderAngle4, rightShoulderAngle5, rightShoulderAngle6)
private var rightHipAngles = arrayOf(rightHipAngle1, rightHipAngle2, rightHipAngle3, rightHipAngle4,
    rightHipAngle5, rightHipAngle6)
private var rightKneeAngles = arrayOf(rightKneeAngle1, rightKneeAngle2, rightKneeAngle3, rightKneeAngle4,
    rightKneeAngle5, rightKneeAngle6)
private var leftKneeAngles = arrayOf(leftKneeAngle1, leftKneeAngle2, leftKneeAngle3,
    leftKneeAngle4, leftKneeAngle5)


private var biggestScore = FloatArray(6)

enum class ModelType {
    Lightning,
    Thunder
}

class MoveNet(private val interpreter: Interpreter, private var gpuDelegate: GpuDelegate?) :
    PoseDetector {

    companion object {
        private const val MIN_CROP_KEYPOINT_SCORE = .2f
        private const val CPU_NUM_THREADS = 4

        // Parameters that control how large crop region should be expanded from previous frames'
        // body keypoints.
        private const val TORSO_EXPANSION_RATIO = 1.9f
        private const val BODY_EXPANSION_RATIO = 1.2f

        // TFLite file names.
        private const val LIGHTNING_FILENAME = "movenet_lightning.tflite"
        private const val THUNDER_FILENAME = "movenet_thunder.tflite"

        private var time = 0
        private var timer: Timer? = null

        // allow specifying model type.
        fun create(context: Context, device: Device, modelType: ModelType): MoveNet {
            val options = Interpreter.Options()
            var gpuDelegate: GpuDelegate? = null
            options.setNumThreads(CPU_NUM_THREADS)
            when (device) {
                Device.CPU -> {
                }
                Device.GPU -> {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                }
                Device.NNAPI -> options.setUseNNAPI(true)
            }

            timer = Timer()
            timer?.scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        time++
                    }
                },
                0,
                1000
            )

            return MoveNet(
                Interpreter(
                    FileUtil.loadMappedFile(
                        context,
                        if (modelType == ModelType.Lightning) LIGHTNING_FILENAME
                        else THUNDER_FILENAME
                    ), options
                ),
                gpuDelegate
            )
        }

        // default to lightning.
        fun create(context: Context, device: Device): MoveNet =
            create(context, device, ModelType.Lightning)

        fun getBiggestScore(poseNum: Int): Float{
            return biggestScore[poseNum]
        }

        // rightAlbowAngles, rightShoulderAngles, rightHipAngles, rightKneeAngles, leftKneeAngles
        fun getRightAlbowAngles(): Array<ArrayList<Float>>{
            return rightAlbowAngles
        }

        fun getRightShoulderAngles(): Array<ArrayList<Float>>{
            return rightShoulderAngles
        }

        fun getRightHipAngles(): Array<ArrayList<Float>>{
            return rightHipAngles
        }

        fun getRightKneeAngles(): Array<ArrayList<Float>>{
            return rightKneeAngles
        }

        fun getLeftKneeAngles(): Array<ArrayList<Float>>{
            return leftKneeAngles
        }
    }

    private var cropRegion: RectF? = null
    private var lastInferenceTimeNanos: Long = -1
    private val inputWidth = interpreter.getInputTensor(0).shape()[1]
    private val inputHeight = interpreter.getInputTensor(0).shape()[2]
    private var outputShape: IntArray = interpreter.getOutputTensor(0).shape()


//    override fun estimatePoses(bitmap: Bitmap): List<Person> {
override fun estimatePoses(bitmap: Bitmap): List<Person> {
        val inferenceStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        if (cropRegion == null) {
            cropRegion = initRectF(bitmap.width, bitmap.height)
        }
        var totalScore = 0f

        val numKeyPoints = outputShape[2]
        val keyPoints = mutableListOf<KeyPoint>()

        cropRegion?.run {
            val rect = RectF(
                (left * bitmap.width),
                (top * bitmap.height),
                (right * bitmap.width),
                (bottom * bitmap.height)
            )
            val detectBitmap = Bitmap.createBitmap(
                rect.width().toInt(),
                rect.height().toInt(),
                Bitmap.Config.ARGB_8888
            )
            Canvas(detectBitmap).drawBitmap(
                bitmap,
                -rect.left,
                -rect.top,
                null
            )
            val inputTensor = processInputImage(detectBitmap, inputWidth, inputHeight)
            val outputTensor = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
            val widthRatio = detectBitmap.width.toFloat() / inputWidth
            val heightRatio = detectBitmap.height.toFloat() / inputHeight

            val positions = mutableListOf<Float>()

            inputTensor?.let { input ->
                interpreter.run(input.buffer, outputTensor.buffer.rewind())
                val output = outputTensor.floatArray
                for (idx in 0 until numKeyPoints) {
                    val x = output[idx * 3 + 1] * inputWidth * widthRatio
                    val y = output[idx * 3 + 0] * inputHeight * heightRatio

                    positions.add(x)
                    positions.add(y)
                    val score = output[idx * 3 + 2]
                    keyPoints.add(
                        KeyPoint(
                            BodyPart.fromInt(idx),
                            PointF(
                                x,
                                y
                            ),
                            score
                        )
                    )
                    totalScore += score
                }
            }
            val matrix = Matrix()
            val points = positions.toFloatArray()

            matrix.postTranslate(rect.left, rect.top)
            matrix.mapPoints(points)
            keyPoints.forEachIndexed { index, keyPoint ->
                keyPoint.coordinate =
                    PointF(
                        points[index * 2],
                        points[index * 2 + 1]
                    )
            }
            // new crop region
            cropRegion = determineRectF(keyPoints, bitmap.width, bitmap.height)
        }
        lastInferenceTimeNanos =
            SystemClock.elapsedRealtimeNanos() - inferenceStartTimeNanos

//        /** 부위별 각도*/
//        val rightElbowAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate, keyPoints[BodyPart.RIGHT_ELBOW.ordinal].coordinate,keyPoints[BodyPart.RIGHT_WRIST.ordinal].coordinate)
//    val rightShoulderAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_ELBOW.ordinal].coordinate, keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate,keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate)
//    val rightHipAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate,keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,keyPoints[BodyPart.RIGHT_KNEE.ordinal].coordinate)
//    val leftKneeAngleArray = arrayOf(keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate,keyPoints[BodyPart.LEFT_KNEE.ordinal].coordinate,keyPoints[BodyPart.LEFT_ANKLE.ordinal].coordinate)
//    val rightKneeAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,keyPoints[BodyPart.RIGHT_KNEE.ordinal].coordinate,keyPoints[BodyPart.RIGHT_ANKLE.ordinal].coordinate)
////        val rightElbowAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_SHOULDER.position].coordinate, keyPoints[BodyPart.RIGHT_ELBOW.position].coordinate,keyPoints[BodyPart.RIGHT_WRIST.position].coordinate)
////    val rightShoulderAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_ELBOW.position].coordinate, keyPoints[BodyPart.RIGHT_SHOULDER.position].coordinate,keyPoints[BodyPart.RIGHT_HIP.position].coordinate)
////    val rightHipAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_SHOULDER.position].coordinate,keyPoints[BodyPart.RIGHT_HIP.positionSS].coordinate,keyPoints[BodyPart.RIGHT_KNEE.position].coordinate)
////    val leftKneeAngleArray = arrayOf(keyPoints[BodyPart.LEFT_HIP.position].coordinate,keyPoints[BodyPart.LEFT_KNEE.position].coordinate,keyPoints[BodyPart.LEFT_ANKLE.position].coordinate)
////    val rightKneeAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_HIP.position].coordinate,keyPoints[BodyPart.RIGHT_KNEE.position].coordinate,keyPoints[BodyPart.RIGHT_ANKLE.position].coordinate)
//    val rightAlbowAngle = getAngle(rightElbowAngleArray)
//    val rightShoulderAngle = getAngle(rightShoulderAngleArray)
//    val rightHipAngle = getAngle(rightHipAngleArray)
//    val leftKneeAngle = getAngle(leftKneeAngleArray)
//    val rightKneeAngle = getAngle(rightKneeAngleArray)
////        val leftAlbowAngle = getAngle(keyPoints[BodyPart.LEFT_SHOULDER.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_ELBOW.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_WRIST.ordinal].coordinate)
//
//
//
////        val leftShoulderAngle = getAngle(keyPoints[BodyPart.LEFT_ELBOW.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_SHOULDER.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate)
//
////        val leftHipAngle = getAngle(keyPoints[BodyPart.LEFT_SHOULDER.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_KNEE.ordinal].coordinate)
//
////        val angleArray = arrayOf(
////            keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate, keyPoints[BodyPart.RIGHT_ELBOW.ordinal].coordinate,keyPoints[BodyPart.RIGHT_WRIST.ordinal].coordinate,
////            keyPoints[BodyPart.RIGHT_ELBOW.ordinal].coordinate, keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate,keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,
////            keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate,keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,keyPoints[BodyPart.RIGHT_KNEE.ordinal].coordinate,
////            keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate,keyPoints[BodyPart.LEFT_KNEE.ordinal].coordinate,keyPoints[BodyPart.LEFT_ANKLE.ordinal].coordinate,
////            keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,keyPoints[BodyPart.RIGHT_KNEE.ordinal].coordinate,keyPoints[BodyPart.RIGHT_ANKLE.ordinal].coordinate
////        )
//
//
//        /** 각 자세 인스턴스 생성*/
//        val pose_address = VowlingPose(90.0f, 0.0f, 160.0f, 160.0f)
//        val pose_pushaway = VowlingPose(105.0f, 15.0f, 150.0f, 150.0f, 150.0f)
//        val pose_downswing = VowlingPose(180.0f, 10.0f, 170.0f, 150.0f, 150.0f)
//        val pose_backswing = VowlingPose(180.0f, 60.0f, 110.0f, 130.0f, 130.0f)
//        val pose_forwardswing = VowlingPose(180.0f, 30.0f, 175.0f, 170.0f, 80.0f)
//        val pose_followthrough = VowlingPose(160.0f, 160.0f, 175.0f, 180.0f, 100.0f)
//
//        /** 각 자세 점수*/
////        val addressScore = pose_address.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle)
////        val pushawayScore = pose_pushaway.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
////        val downswingScore = pose_downswing.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
////        val backswingScore = pose_backswing.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
////        val forwardswingScore = pose_forwardswing.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
////        val followthroughScore = pose_followthrough.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
//
//
//
//         if(time<7) {
////            if (biggestScore[0] < addressScore)
////                biggestScore[0] = addressScore
//            return listOf(Person(keyPoints = keyPoints, score = getScore(pose_address, rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle)))
//        }
//        else if(time<14) {
////            if (biggestScore[1] < pushawayScore)
////                biggestScore[1] = pushawayScore
//            return listOf(Person(keyPoints = keyPoints, score = getScore(pose_pushaway, rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)))
//        }
//        else if(time<21) {
////            if (biggestScore[2] < downswingScore)
////                biggestScore[2] = downswingScore
//            return listOf(Person(keyPoints = keyPoints, score = getScore(pose_downswing, rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)))
//        }
//        else if(time<28) {
////            if (biggestScore[3] < backswingScore)
////                biggestScore[3] = backswingScore
//                return listOf(Person(keyPoints = keyPoints, score = getScore(pose_backswing, rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)))
//        }
//        else if(time<35) {
////            if (biggestScore[4] < forwardswingScore)
////                biggestScore[4] = forwardswingScore
//            return listOf(Person(keyPoints = keyPoints, score = getScore(pose_forwardswing, rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)))
//        }
//        else if(time<42){
////            if (biggestScore[5] < followthroughScore)
////                biggestScore[5] = followthroughScore
//            return listOf(Person(keyPoints = keyPoints, score = getScore(pose_followthrough, rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)))
//        }
//        else{ //42 초 이후로는 카메라 종료되게 하면 좋을것 같아요
//            return listOf(Person(keyPoints = keyPoints, score = 100.0f))
//        }




    /** 부위별 각도*/
    val rightElbowAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_SHOULDER.position].coordinate, keyPoints[BodyPart.RIGHT_ELBOW.position].coordinate,keyPoints[BodyPart.RIGHT_WRIST.position].coordinate)
    val rightShoulderAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_ELBOW.position].coordinate, keyPoints[BodyPart.RIGHT_SHOULDER.position].coordinate,keyPoints[BodyPart.RIGHT_HIP.position].coordinate)
    val rightHipAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_SHOULDER.position].coordinate,keyPoints[BodyPart.RIGHT_HIP.position].coordinate,keyPoints[BodyPart.RIGHT_KNEE.position].coordinate)
    val leftKneeAngleArray = arrayOf(keyPoints[BodyPart.LEFT_HIP.position].coordinate,keyPoints[BodyPart.LEFT_KNEE.position].coordinate,keyPoints[BodyPart.LEFT_ANKLE.position].coordinate)
    val rightKneeAngleArray = arrayOf(keyPoints[BodyPart.RIGHT_HIP.position].coordinate,keyPoints[BodyPart.RIGHT_KNEE.position].coordinate,keyPoints[BodyPart.RIGHT_ANKLE.position].coordinate)
    val rightAlbowAngle = getAngle(rightElbowAngleArray)
    val rightShoulderAngle = getAngle(rightShoulderAngleArray)
    val rightHipAngle = getAngle(rightHipAngleArray)
    val leftKneeAngle = getAngle(leftKneeAngleArray)
    val rightKneeAngle = getAngle(rightKneeAngleArray)
//        val leftAlbowAngle = getAngle(keyPoints[BodyPart.LEFT_SHOULDER.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_ELBOW.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_WRIST.ordinal].coordinate)



//        val leftShoulderAngle = getAngle(keyPoints[BodyPart.LEFT_ELBOW.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_SHOULDER.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate)



//        val leftHipAngle = getAngle(keyPoints[BodyPart.LEFT_SHOULDER.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_KNEE.ordinal].coordinate)



//        val angleArray = arrayOf(
//            keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate, keyPoints[BodyPart.RIGHT_ELBOW.ordinal].coordinate,keyPoints[BodyPart.RIGHT_WRIST.ordinal].coordinate,
//            keyPoints[BodyPart.RIGHT_ELBOW.ordinal].coordinate, keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate,keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,
//            keyPoints[BodyPart.RIGHT_SHOULDER.ordinal].coordinate,keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,keyPoints[BodyPart.RIGHT_KNEE.ordinal].coordinate,
//            keyPoints[BodyPart.LEFT_HIP.ordinal].coordinate,keyPoints[BodyPart.LEFT_KNEE.ordinal].coordinate,keyPoints[BodyPart.LEFT_ANKLE.ordinal].coordinate,
//            keyPoints[BodyPart.RIGHT_HIP.ordinal].coordinate,keyPoints[BodyPart.RIGHT_KNEE.ordinal].coordinate,keyPoints[BodyPart.RIGHT_ANKLE.ordinal].coordinate
//        )

    /** 각 자세 인스턴스 생성*/
    val pose_address = VowlingPose(90.0f, 0.0f, 160.0f, 160.0f)
    val pose_pushaway = VowlingPose(105.0f, 15.0f, 150.0f, 150.0f, 150.0f)
    val pose_downswing = VowlingPose(180.0f, 10.0f, 170.0f, 150.0f, 150.0f)
    val pose_backswing = VowlingPose(180.0f, 60.0f, 110.0f, 130.0f, 130.0f)
    val pose_forwardswing = VowlingPose(180.0f, 30.0f, 175.0f, 170.0f, 80.0f)
    val pose_followthrough = VowlingPose(160.0f, 160.0f, 175.0f, 180.0f, 100.0f)

    /** 각 자세 점수*/
//        val addressScore = pose_address.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle)
//        val pushawayScore = pose_pushaway.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
//        val downswingScore = pose_downswing.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
//        val backswingScore = pose_backswing.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
//        val forwardswingScore = pose_forwardswing.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)
//        val followthroughScore = pose_followthrough.getScore(rightAlbowAngle, rightShoulderAngle, rightHipAngle, rightKneeAngle, leftKneeAngle)


    // rightAlbowAngles, rightShoulderAngles, rightHipAngles, rightKneeAngles, leftKneeAngles
    if(time < 7) {

        Log.d("TAG", "estimatePoses: ${rightAlbowAngle}")
        rightAlbowAngles[0].add(rightAlbowAngle)
        rightShoulderAngles[0].add(rightShoulderAngle)
        rightHipAngles[0].add(rightHipAngle)
        rightKneeAngles[0].add(rightKneeAngle)
//             return listOf(Person(keyPoints = keyPoints, score = addressScore.toFloat()))
//                 return listOf(Person(keyPoints = keyPoints, score = 100.0f))
        Log.d("TAG", "estimatePoses: ${rightAlbowAngles[0][0]}")
    }

    else if(time < 14) {
        rightAlbowAngles[1].plus(rightAlbowAngle)
        rightShoulderAngles[1].plus(rightShoulderAngle)
        rightHipAngles[1].plus(rightHipAngle)
        rightKneeAngles[1].plus(rightKneeAngle)
        leftKneeAngles[0].plus(leftKneeAngle)
//             return listOf(Person(keyPoints = keyPoints, score = pushawayScore.toFloat()))
//             return listOf(Person(keyPoints = keyPoints, score = 100.0f))
    }

    else if(time < 21) {
        rightAlbowAngles[2].plus(rightAlbowAngle)
        rightShoulderAngles[2].plus(rightShoulderAngle)
        rightHipAngles[2].plus(rightHipAngle)
        rightKneeAngles[2].plus(rightKneeAngle)
        leftKneeAngles[1].plus(leftKneeAngle)
//        return listOf(Person(keyPoints = keyPoints, score = downswingScore.toFloat()))
//             return listOf(Person(keyPoints = keyPoints, score = 100.0f))
    }
    else if(time < 28) {
        rightAlbowAngles[3].plus(rightAlbowAngle)
        rightShoulderAngles[3].plus(rightShoulderAngle)
        rightHipAngles[3].plus(rightHipAngle)
        rightKneeAngles[3].plus(rightKneeAngle)
        leftKneeAngles[2].plus(leftKneeAngle)
//        return listOf(Person(keyPoints = keyPoints, score = backswingScore.toFloat()))
//             return listOf(Person(keyPoints = keyPoints, score = 100.0f))
    }
    else if(time < 35) {
        rightAlbowAngles[4].plus(rightAlbowAngle)
        rightShoulderAngles[4].plus(rightShoulderAngle)
        rightHipAngles[4].plus(rightHipAngle)
        rightKneeAngles[4].plus(rightKneeAngle)
        leftKneeAngles[3].plus(leftKneeAngle)
//        return listOf(Person(keyPoints = keyPoints, score = forwardswingScore.toFloat()))
//             return listOf(Person(keyPoints = keyPoints, score = 100.0f))
    }
    else if(time < 42){
        rightAlbowAngles[5].plus(rightAlbowAngle)
        rightShoulderAngles[5].plus(rightShoulderAngle)
        rightHipAngles[5].plus(rightHipAngle)
        rightKneeAngles[5].plus(rightKneeAngle)
        leftKneeAngles[4].plus(leftKneeAngle)
//        return listOf(Person(keyPoints = keyPoints, score = followthroughScore.toFloat()))
//             return listOf(Person(keyPoints = keyPoints, score = 100.0f))
    }
//        else{ //42 초 이후로는 카메라 종료되게 하면 좋을것 같아요
//             return listOf(Person(keyPoints = keyPoints, score = 100.0f))
//        }
    return listOf(Person(keyPoints = keyPoints, score = 100.0f))


//    return listOf(Person(keyPoints = keyPoints, score = addressScore.toFloat()))
//    return listOf(Person(keyPoints = keyPoints, score = totalScore / numKeyPoints))
    }

//    fun getAngle(a1: PointF, a2: PointF, a3: PointF): Double {
//        val p1: Double = hypot(((a1.x) - (a2.x)).toDouble(), ((a1.y) - (a2.y)).toDouble())
//        val p2: Double = hypot(((a2.x) - (a3.x)).toDouble(), ((a2.y) - (a3.y)).toDouble())
//        val p3: Double = hypot(((a3.x) - (a1.x)).toDouble(), ((a3.y) - (a1.y)).toDouble())
//        val radian: Double = acos((p1 * p1 + p2 * p2 - p3 * p3) / (2 * p1 * p2))
//        return radian / PI * 180
//    }
//    fun getAngle(angleArray: Array<PointF>): Double {
//    Log.d("TAG", "getAngle: ${angleArray[0]}, ${angleArray[1]}, ${angleArray[2]}")
//        val p1: Double = hypot(((angleArray[0].x) - (angleArray[1].x)).toDouble(), ((angleArray[0].y) - (angleArray[1].y)).toDouble())
//        val p2: Double = hypot(((angleArray[1].x) - (angleArray[2].x)).toDouble(), ((angleArray[1].y) - (angleArray[2].y)).toDouble())
//        val p3: Double = hypot(((angleArray[2].x) - (angleArray[0].x)).toDouble(), ((angleArray[2].y) - (angleArray[0].y)).toDouble())
//        val radian: Double = acos((p1 * p1 + p2 * p2 - p3 * p3) / (2 * p1 * p2))
//        return radian / PI * 180
//    }

    fun getAngle(angleArray: Array<PointF>): Float {
//        Log.d("TAG", "getAngle: ${angleArray[0]}, ${angleArray[1]}, ${angleArray[2]}")
        val p1: Float = hypot(((angleArray[0].x) - (angleArray[1].x)), ((angleArray[0].y) - (angleArray[1].y)))
        val p2: Float = hypot(((angleArray[1].x) - (angleArray[2].x)), ((angleArray[1].y) - (angleArray[2].y)))
        val p3: Float = hypot(((angleArray[2].x) - (angleArray[0].x)), ((angleArray[2].y) - (angleArray[0].y)))
        val radian: Float = acos((p1 * p1 + p2 * p2 - p3 * p3) / (2 * p1 * p2))
        return radian / PI.toFloat() * 180.0f
    }

    fun getScore(pose: VowlingPose, a1: Float, a2: Float, a3: Float, a4: Float): Float {
        var pose1 = pose.correctRightElbowAngle - a1
        if(pose1 < 0.0f) {
            pose1 = -pose1
        }

        var pose2 = pose.correctRightShoulderAngle - a2
        if(pose2 < 0.0f) {
            pose2 = -pose2
        }

        var pose3 = pose.correctRightHipAngle - a3
        if(pose3 < 0.0f) {
            pose3 = -pose3
        }

        var pose4 = pose.correctRightKneeAngle - a4
        if(pose4 < 0.0f) {
            pose4 = -pose4
        }

        var result = 100 - (pose1 + pose2 + pose3 + pose4)
        if (result < 0.0f) {
            result = 0.0f
        } else if(result > 100.0f) {
            result = 100.0f
        }

        return result

    }

    fun getScore(pose: VowlingPose, a1: Float, a2: Float, a3: Float, a4: Float, a5: Float): Float {
        var pose1 = pose.correctRightElbowAngle - a1
        if(pose1 < 0) {
            pose1 = -pose1
        }

        var pose2 = pose.correctRightShoulderAngle - a2
        if(pose2 < 0) {
            pose2 = -pose2
        }

        var pose3 = pose.correctRightHipAngle - a3
        if(pose3 < 0) {
            pose3 = -pose3
        }

        var pose4 = pose.correctRightKneeAngle - a4
        if(pose4 < 0) {
            pose4 = -pose4
        }

        var pose5 = pose.correctLeftKneeAngle - a5
        if(pose5 < 0) {
            pose5 = -pose5
        }
        var result = 100 - (pose1 + pose2 + pose3 + pose4 + pose5)
        if (result < 0.0f) {
            result = 0.0f
        } else if(result > 100.0f) {
            result = 100.0f
        }

        return result
    }

    override fun lastInferenceTimeNanos(): Long = lastInferenceTimeNanos

    override fun close() {
        gpuDelegate?.close()
        interpreter.close()
        cropRegion = null
        time = 0
        timer = null
    }

    /**
     * Prepare input image for detection
     */
    private fun processInputImage(bitmap: Bitmap, inputWidth: Int, inputHeight: Int): TensorImage? {
        val width: Int = bitmap.width
        val height: Int = bitmap.height

        val size = if (height > width) width else height
        val imageProcessor = ImageProcessor.Builder().apply {
            add(ResizeWithCropOrPadOp(size, size))
            add(ResizeOp(inputWidth, inputHeight, ResizeOp.ResizeMethod.BILINEAR))
        }.build()
        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(bitmap)
        return imageProcessor.process(tensorImage)
    }

    /**
     * Defines the default crop region.
     * The function provides the initial crop region (pads the full image from both
     * sides to make it a square image) when the algorithm cannot reliably determine
     * the crop region from the previous frame.
     */
    private fun initRectF(imageWidth: Int, imageHeight: Int): RectF {
        val xMin: Float
        val yMin: Float
        val width: Float
        val height: Float
        if (imageWidth > imageHeight) {
            width = 1f
            height = imageWidth.toFloat() / imageHeight
            xMin = 0f
            yMin = (imageHeight / 2f - imageWidth / 2f) / imageHeight
        } else {
            height = 1f
            width = imageHeight.toFloat() / imageWidth
            yMin = 0f
            xMin = (imageWidth / 2f - imageHeight / 2) / imageWidth
        }
        return RectF(
            xMin,
            yMin,
            xMin + width,
            yMin + height
        )
    }


    /**
     * Checks whether there are enough torso keypoints.
     * This function checks whether the model is confident at predicting one of the
     * shoulders/hips which is required to determine a good crop region.
     */
    private fun torsoVisible(keyPoints: List<KeyPoint>): Boolean {
        return ((keyPoints[BodyPart.LEFT_HIP.position].score > MIN_CROP_KEYPOINT_SCORE).or(
            keyPoints[BodyPart.RIGHT_HIP.position].score > MIN_CROP_KEYPOINT_SCORE
        )).and(
            (keyPoints[BodyPart.LEFT_SHOULDER.position].score > MIN_CROP_KEYPOINT_SCORE).or(
                keyPoints[BodyPart.RIGHT_SHOULDER.position].score > MIN_CROP_KEYPOINT_SCORE
            )
        )
    }

    /**
     * Determines the region to crop the image for the model to run inference on.
     * The algorithm uses the detected joints from the previous frame to estimate
     * the square region that encloses the full body of the target person and
     * centers at the midpoint of two hip joints. The crop size is determined by
     * the distances between each joints and the center point.
     * When the model is not confident with the four torso joint predictions, the
     * function returns a default crop which is the full image padded to square.
     */
    private fun determineRectF(
        keyPoints: List<KeyPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): RectF {
        val targetKeyPoints = mutableListOf<KeyPoint>()
        keyPoints.forEach {
            targetKeyPoints.add(
                KeyPoint(
                    it.bodyPart,
                    PointF(
                        it.coordinate.x,
                        it.coordinate.y
                    ),
                    it.score
                )
            )
        }
        if (torsoVisible(keyPoints)) {
            val centerX =
                (targetKeyPoints[BodyPart.LEFT_HIP.position].coordinate.x +
                        targetKeyPoints[BodyPart.RIGHT_HIP.position].coordinate.x) / 2f
            val centerY =
                (targetKeyPoints[BodyPart.LEFT_HIP.position].coordinate.y +
                        targetKeyPoints[BodyPart.RIGHT_HIP.position].coordinate.y) / 2f

            val torsoAndBodyDistances =
                determineTorsoAndBodyDistances(keyPoints, targetKeyPoints, centerX, centerY)

            val list = listOf(
                torsoAndBodyDistances.maxTorsoXDistance * TORSO_EXPANSION_RATIO,
                torsoAndBodyDistances.maxTorsoYDistance * TORSO_EXPANSION_RATIO,
                torsoAndBodyDistances.maxBodyXDistance * BODY_EXPANSION_RATIO,
                torsoAndBodyDistances.maxBodyYDistance * BODY_EXPANSION_RATIO
            )

            var cropLengthHalf = list.maxOrNull() ?: 0f
            val tmp = listOf(centerX, imageWidth - centerX, centerY, imageHeight - centerY)
            cropLengthHalf = min(cropLengthHalf, tmp.maxOrNull() ?: 0f)
            val cropCorner = Pair(centerY - cropLengthHalf, centerX - cropLengthHalf)

            return if (cropLengthHalf > max(imageWidth, imageHeight) / 2f) {
                initRectF(imageWidth, imageHeight)
            } else {
                val cropLength = cropLengthHalf * 2
                RectF(
                    cropCorner.second / imageWidth,
                    cropCorner.first / imageHeight,
                    (cropCorner.second + cropLength) / imageWidth,
                    (cropCorner.first + cropLength) / imageHeight,
                )
            }
        } else {
            return initRectF(imageWidth, imageHeight)
        }
    }

    /**
     * Calculates the maximum distance from each keypoints to the center location.
     * The function returns the maximum distances from the two sets of keypoints:
     * full 17 keypoints and 4 torso keypoints. The returned information will be
     * used to determine the crop size. See determineRectF for more detail.
     */
    private fun determineTorsoAndBodyDistances(
        keyPoints: List<KeyPoint>,
        targetKeyPoints: List<KeyPoint>,
        centerX: Float,
        centerY: Float
    ): TorsoAndBodyDistance {
        val torsoJoints = listOf(
            BodyPart.LEFT_SHOULDER.position,
            BodyPart.RIGHT_SHOULDER.position,
            BodyPart.LEFT_HIP.position,
            BodyPart.RIGHT_HIP.position
        )

        var maxTorsoYRange = 0f
        var maxTorsoXRange = 0f
        torsoJoints.forEach { joint ->
            val distY = abs(centerY - targetKeyPoints[joint].coordinate.y)
            val distX = abs(centerX - targetKeyPoints[joint].coordinate.x)
            if (distY > maxTorsoYRange) maxTorsoYRange = distY
            if (distX > maxTorsoXRange) maxTorsoXRange = distX
        }

        var maxBodyYRange = 0f
        var maxBodyXRange = 0f
        for (joint in keyPoints.indices) {
            if (keyPoints[joint].score < MIN_CROP_KEYPOINT_SCORE) continue
            val distY = abs(centerY - keyPoints[joint].coordinate.y)
            val distX = abs(centerX - keyPoints[joint].coordinate.x)

            if (distY > maxBodyYRange) maxBodyYRange = distY
            if (distX > maxBodyXRange) maxBodyXRange = distX
        }
        return TorsoAndBodyDistance(
            maxTorsoYRange,
            maxTorsoXRange,
            maxBodyYRange,
            maxBodyXRange
        )
    }
}
