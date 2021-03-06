/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.core

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Looper.getMainLooper
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.impl.CameraFactory
import androidx.camera.core.impl.CameraThreadConfig
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.fakes.FakeAppConfig
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager
import androidx.camera.testing.fakes.FakeCameraFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument
import org.robolectric.Shadows.shadowOf
import java.util.Collections
import java.util.concurrent.ExecutionException

/**
 * Unit tests for [Preview].
 */
@SmallTest
@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
@Config(
    minSdk = Build.VERSION_CODES.LOLLIPOP, shadows = [ShadowCameraX::class]
)
class PreviewTest {

    @Before
    @Throws(ExecutionException::class, InterruptedException::class)
    fun setUp() {
        val cameraFactoryProvider =
            CameraFactory.Provider { _: Context?, _: CameraThreadConfig? ->
                val cameraFactory = FakeCameraFactory()
                cameraFactory.insertDefaultBackCamera(
                    ShadowCameraX.DEFAULT_CAMERA_ID
                ) { FakeCamera(ShadowCameraX.DEFAULT_CAMERA_ID) }
                cameraFactory
            }
        val cameraXConfig = CameraXConfig.Builder.fromConfig(
            FakeAppConfig.create()
        ).setCameraFactoryProvider(cameraFactoryProvider).build()
        val context = ApplicationProvider.getApplicationContext<Context>()
        CameraX.initialize(context, cameraXConfig).get()
    }

    @After
    @Throws(ExecutionException::class, InterruptedException::class)
    fun tearDown() {
        CameraX.shutdown().get()
    }

    @Test
    fun viewPortSet_cropRectIsBasedOnViewPort() {
        val transformationInfo = bindToLifecycleAndGetTransformationInfo(
            ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0).build()
        )
        // The expected value is based on fitting the 1:1 view port into a rect with the size of
        // FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.
        val expectedPadding = (FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.width -
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.height) / 2
        assertThat(transformationInfo.cropRect).isEqualTo(
            Rect(
                expectedPadding,
                0,
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.width - expectedPadding,
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.height
            )
        )
    }

    @Test
    fun viewPortNotSet_cropRectIsFullSurface() {
        val transformationInfo = bindToLifecycleAndGetTransformationInfo(
            null
        )

        assertThat(transformationInfo.cropRect).isEqualTo(
            Rect(
                0,
                0,
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.width,
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.height
            )
        )
    }

    @Test
    fun surfaceRequestSize_isSurfaceSize() {
        assertThat(bindToLifecycleAndGetSurfaceRequest().resolution).isEqualTo(
            Size(
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.width,
                FakeCameraDeviceSurfaceManager.MAX_OUTPUT_SIZE.height
            )
        )
    }

    @Test
    fun setTargetRotation_rotationIsChanged() {
        // Arrange.
        val preview = Preview.Builder().setTargetRotation(Surface.ROTATION_0).build()

        // Act: set target rotation.
        preview.targetRotation = Surface.ROTATION_180

        // Assert: target rotation is updated.
        assertThat(preview.targetRotation).isEqualTo(Surface.ROTATION_180)
    }

    @Test
    fun setSurfaceProviderAfterAttachment_receivesSurfaceProviderCallbacks() {
        // Arrange: attach Preview without a SurfaceProvider.
        val preview = Preview.Builder().setTargetRotation(Surface.ROTATION_0).build()
        val cameraUseCaseAdapter = CameraUtil.getCameraUseCaseAdapter(
            ApplicationProvider
                .getApplicationContext<Context>(), CameraSelector.DEFAULT_BACK_CAMERA
        )
        cameraUseCaseAdapter.addUseCases(Collections.singleton<UseCase>(preview))

        // Get pending SurfaceRequest created by pipeline.
        val pendingSurfaceRequest = preview.mCurrentSurfaceRequest
        var receivedSurfaceRequest: SurfaceRequest? = null
        var receivedTransformationInfo: SurfaceRequest.TransformationInfo? = null

        // Act: set a SurfaceProvider after attachment.
        preview.setSurfaceProvider { request ->
            request.setTransformationInfoListener(
                CameraXExecutors.directExecutor(),
                SurfaceRequest.TransformationInfoListener {
                    receivedTransformationInfo = it
                })
            receivedSurfaceRequest = request
        }
        shadowOf(getMainLooper()).idle()

        // Assert: received SurfaceRequest is the pending SurfaceRequest.
        assertThat(receivedSurfaceRequest).isSameInstanceAs(pendingSurfaceRequest)
        assertThat(receivedTransformationInfo).isNotNull()

        // Act: set a different SurfaceProvider.
        preview.setSurfaceProvider { request ->
            request.setTransformationInfoListener(
                CameraXExecutors.directExecutor(),
                SurfaceRequest.TransformationInfoListener {
                    receivedTransformationInfo = it
                })
            receivedSurfaceRequest = request
        }
        shadowOf(getMainLooper()).idle()

        // Assert: received a different SurfaceRequest.
        assertThat(receivedSurfaceRequest).isNotSameInstanceAs(pendingSurfaceRequest)
    }

    private fun bindToLifecycleAndGetSurfaceRequest(): SurfaceRequest {
        return bindToLifecycleAndGetResult(null).first
    }

    private fun bindToLifecycleAndGetTransformationInfo(viewPort: ViewPort?):
            SurfaceRequest.TransformationInfo {
        return bindToLifecycleAndGetResult(viewPort).second
    }

    private fun bindToLifecycleAndGetResult(viewPort: ViewPort?): Pair<SurfaceRequest,
            SurfaceRequest.TransformationInfo> {
        // Arrange.
        val preview = Preview.Builder().setTargetRotation(Surface.ROTATION_0).build()
        var surfaceRequest: SurfaceRequest? = null
        var transformationInfo: SurfaceRequest.TransformationInfo? = null
        preview.setSurfaceProvider { request ->
            request.setTransformationInfoListener(
                CameraXExecutors.directExecutor(),
                SurfaceRequest.TransformationInfoListener {
                    transformationInfo = it
                })
            surfaceRequest = request
        }

        // Act.
        val cameraUseCaseAdapter = CameraUtil.getCameraUseCaseAdapter(
            ApplicationProvider
                .getApplicationContext<Context>(), CameraSelector.DEFAULT_BACK_CAMERA
        )

        cameraUseCaseAdapter.setViewPort(viewPort)
        cameraUseCaseAdapter.addUseCases(Collections.singleton<UseCase>(preview))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return Pair(surfaceRequest!!, transformationInfo!!)
    }
}