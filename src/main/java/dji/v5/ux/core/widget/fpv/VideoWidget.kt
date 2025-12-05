package dji.v5.ux.core.widget.fpv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.core.content.res.use
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.flightassistant.VisionAssistDirection
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.utils.common.LogPath
import dji.v5.utils.common.LogUtils
import dji.v5.ux.R
import dji.v5.ux.core.base.DJISDKModel
import dji.v5.ux.core.base.SchedulerProvider
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore
import dji.v5.ux.core.extension.getBooleanAndUse
import dji.v5.ux.core.extension.getColor
import dji.v5.ux.core.extension.getColorAndUse
import dji.v5.ux.core.extension.getDimensionAndUse
import dji.v5.ux.core.extension.getFloatAndUse
import dji.v5.ux.core.extension.getIntegerAndUse
import dji.v5.ux.core.extension.getResourceIdAndUse
import dji.v5.ux.core.extension.getString
import dji.v5.ux.core.module.FlatCameraModule
import dji.v5.ux.core.ui.CenterPointView
import dji.v5.ux.core.ui.GridLineView
import dji.v5.ux.core.widget.fpv.FPVWidget.ModelState
import dji.v5.ux.databinding.UxsdkWidgetVideoBinding

private const val TAG = "VideoWidget"
private const val LANDSCAPE_ROTATION_ANGLE = 0

open class VideoWidget @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayoutWidget<ModelState>(context, attrs, defStyleAttr) {
    private val mBinding = UxsdkWidgetVideoBinding.inflate(LayoutInflater.from(context), this)
    private var viewWidth = 0
    private var viewHeight = 0
    private var rotationAngle = 0
    private var surface: Surface? = null
    private var width = -1
    private var height = -1
    private var fpvStateChangeResourceId: Int = INVALID_RESOURCE
    private val cameraSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
            LogUtils.i(LogPath.SAMPLE, "surfaceCreated: ${widgetModel.getCameraIndex()}")
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this@VideoWidget.width = width
            this@VideoWidget.height = height
            LogUtils.i(
                LogPath.SAMPLE,
                "surfaceChanged: ${widgetModel.getCameraIndex()}",
                "width:$width",
                ",height:$height"
            )
            updateCameraStream()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            width = 0
            height = 0
            LogUtils.i(LogPath.SAMPLE, "surfaceDestroyed: ${widgetModel.getCameraIndex()}")
            removeSurfaceBinding()
        }
    }

    val widgetModel: VideoWidgetModel = VideoWidgetModel(
        DJISDKModel.getInstance(), ObservableInMemoryKeyedStore.getInstance(), FlatCameraModule()
    )
    var showAssistControl: Boolean = true
        set(value) {
            if (widgetModel.getCameraIndex() == ComponentIndexType.VISION_ASSIST && value){
                mBinding.assistControl.visibility = View.VISIBLE
            }else{
                mBinding.assistControl.visibility = View.GONE
            }
            field = value
        }
    var directionSelectedColor = getColor(R.color.uxsdk_orange_in_dark)
    var directionColor = getColor(R.color.uxsdk_white)
    var isGridLinesEnabled = true
        set(isGridLinesEnabled) {
            field = isGridLinesEnabled
            updateGridLineVisibility()
        }
    var isCenterPointEnabled = false
        set(isCenterPointEnabled) {
            field = isCenterPointEnabled
            mBinding.viewCenterPoint.visibility =
                if (isCenterPointEnabled) View.VISIBLE else View.GONE
        }
    var verticalMargin: Int
        get() {
            val layoutParams: LayoutParams = mBinding.btnDirectionFront.layoutParams as LayoutParams
            return layoutParams.bottomMargin
        }
        set(value) {
            val layoutParams: LayoutParams = mBinding.btnDirectionFront.layoutParams as LayoutParams
            layoutParams.bottomMargin = value
            mBinding.btnDirectionFront.layoutParams = layoutParams

            val layoutParams2: LayoutParams = mBinding.btnDirectionBack.layoutParams as LayoutParams
            layoutParams2.topMargin = value
            mBinding.btnDirectionBack.layoutParams = layoutParams2
        }
    var horizontalMargin: Int
        get() {
            val layoutParams: LayoutParams = mBinding.btnDirectionLeft.layoutParams as LayoutParams
            return layoutParams.marginEnd
        }
        set(value) {
            val layoutParams: LayoutParams = mBinding.btnDirectionLeft.layoutParams as LayoutParams
            layoutParams.marginEnd = value
            mBinding.btnDirectionLeft.layoutParams = layoutParams

            val layoutParams2: LayoutParams = mBinding.btnDirectionRight.layoutParams as LayoutParams
            layoutParams2.marginStart = value
            mBinding.btnDirectionRight.layoutParams = layoutParams2
        }
    var currentDirectionMargin: Int
        get() {
            val layoutParams: LayoutParams = mBinding.tvCurrentDirection.layoutParams as LayoutParams
            return layoutParams.bottomMargin
        }
        set(value) {
            val layoutParams: LayoutParams = mBinding.tvCurrentDirection.layoutParams as LayoutParams
            layoutParams.bottomMargin = value
            mBinding.tvCurrentDirection.layoutParams = layoutParams
        }

    override fun initView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {

    }

    private fun updateButtonState(state: VisionAssistDirection) {
        with(mBinding) {
            btnDirectionFront.rotation = 0f
            btnDirectionFront.setColorFilter(directionColor, PorterDuff.Mode.SRC_IN)
            btnDirectionBack.rotation = 180f
            btnDirectionBack.setColorFilter(directionColor, PorterDuff.Mode.SRC_IN)
            btnDirectionLeft.rotation = -90f
            btnDirectionLeft.setColorFilter(directionColor, PorterDuff.Mode.SRC_IN)
            btnDirectionRight.rotation = 90f
            btnDirectionRight.setColorFilter(directionColor, PorterDuff.Mode.SRC_IN)
            btnDirectionDown.setColorFilter(directionColor, PorterDuff.Mode.SRC_IN)

            when (state) {
                VisionAssistDirection.FRONT -> {
                    btnDirectionFront.rotation = 0f
                    btnDirectionFront.setColorFilter(directionSelectedColor, PorterDuff.Mode.SRC_IN)
                    tvCurrentDirection.text = "前"
                }

                VisionAssistDirection.BACK -> {
                    btnDirectionBack.rotation = 180f
                    btnDirectionBack.setColorFilter(directionSelectedColor, PorterDuff.Mode.SRC_IN)
                    tvCurrentDirection.text = "后"
                }

                VisionAssistDirection.LEFT -> {
                    btnDirectionLeft.rotation = -90f
                    btnDirectionLeft.setColorFilter(directionSelectedColor, PorterDuff.Mode.SRC_IN)
                    tvCurrentDirection.text = "左"
                }

                VisionAssistDirection.RIGHT -> {
                    btnDirectionRight.rotation = 90f
                    btnDirectionRight.setColorFilter(directionSelectedColor, PorterDuff.Mode.SRC_IN)
                    tvCurrentDirection.text = "右"
                }
                VisionAssistDirection.DOWN -> {
                    tvCurrentDirection.text = "下"
                    btnDirectionDown.setColorFilter(directionSelectedColor, PorterDuff.Mode.SRC_IN)
                }
                VisionAssistDirection.AUTO -> {
                    tvCurrentDirection.text = "自动"
                }

                VisionAssistDirection.OFF -> {
                    tvCurrentDirection.text = "关闭"
                }
                VisionAssistDirection.UP -> {
                    tvCurrentDirection.text = "上"
                }
                VisionAssistDirection.UNKNOWN -> {
                    tvCurrentDirection.text = "未知"
                }
            }
        }
    }

    init {
        if (!isInEditMode) {
            rotationAngle = LANDSCAPE_ROTATION_ANGLE
            mBinding.surfaceViewFpv.holder.addCallback(cameraSurfaceCallback)
        }
        attrs?.let { initAttributes(context, it) }
        mBinding.btnDirectionFront.setOnClickListener {  widgetModel.setVisionAssistViewDirection(VisionAssistDirection.FRONT) }
        mBinding.btnDirectionBack.setOnClickListener { widgetModel.setVisionAssistViewDirection(VisionAssistDirection.BACK) }
        mBinding.btnDirectionLeft.setOnClickListener { widgetModel.setVisionAssistViewDirection(VisionAssistDirection.LEFT) }
        mBinding.btnDirectionRight.setOnClickListener {widgetModel.setVisionAssistViewDirection(VisionAssistDirection.RIGHT) }
        mBinding.btnDirectionDown.setOnClickListener { widgetModel.setVisionAssistViewDirection(VisionAssistDirection.DOWN) }
    }
    //endregion

    //region LifeCycle
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            widgetModel.setup()
        }
        initializeListeners()
    }

    private fun initializeListeners() {
        //后面补上
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        mBinding.surfaceViewFpv.visibility = visibility
    }

    override fun onDetachedFromWindow() {
        destroyListeners()
        if (!isInEditMode) {
            widgetModel.cleanup()
        }
        super.onDetachedFromWindow()
    }

    override fun reactToModelChanges() {
        addReaction(
            widgetModel.displayMsgProcessor.toFlowable().observeOn(SchedulerProvider.ui())
                .subscribe { cameraName: String -> updateCameraName(cameraName) })
        addReaction(
            widgetModel.displayAssistantProcessor.toFlowable().observeOn(SchedulerProvider.ui())
                .subscribe { show: Boolean -> updateAssistantDisplay(show) })
        addReaction(
            widgetModel.cameraSideProcessor.toFlowable().observeOn(SchedulerProvider.ui())
                .subscribe { cameraSide: String -> updateCameraSide(cameraSide) })
        addReaction(
            widgetModel.hasVideoViewChanged.observeOn(SchedulerProvider.ui())
                .subscribe { delayCalculator() })
        addReaction(
            widgetModel.assistViewDirectionProcessor.toFlowable().observeOn(SchedulerProvider.ui())
                .subscribe { visionAssistDirection: VisionAssistDirection ->
                    updateButtonState(visionAssistDirection)
                })
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!isInEditMode) {
            setViewDimensions()
            delayCalculator()
        }
    }

    private fun updateCameraSide(string: String) {

    }

    private fun destroyListeners() {
        //后面补上
    }

    //endregion
    //region Customization
    override fun getIdealDimensionRatioString(): String {
        return getString(R.string.uxsdk_widget_fpv_ratio)
    }

    fun updateVideoSource(source: ComponentIndexType) {
        LogUtils.i(LogPath.SAMPLE, "updateVideoSource", source, this)
        widgetModel.updateCameraSource(source, CameraLensType.UNKNOWN)

        if ((source == ComponentIndexType.VISION_ASSIST || source == ComponentIndexType.FPV) && showAssistControl) {
            mBinding.assistControl.visibility = VISIBLE
//            if (!isInitialized){
//                toggleButtonState(0)
//                isInitialized = true
//            }
        } else {
            mBinding.assistControl.visibility = GONE
        }
        updateCameraStream()
        if (source == ComponentIndexType.VISION_ASSIST) {
            widgetModel.enableVisionAssist()
        }
        mBinding.surfaceViewFpv.invalidate()
    }

    fun setOnFPVStreamSourceListener(listener: FPVStreamSourceListener) {
        widgetModel.streamSourceListener = listener
    }

    fun setSurfaceViewZOrderOnTop(onTop: Boolean) {
        mBinding.surfaceViewFpv.setZOrderOnTop(onTop)
    }

    fun setSurfaceViewZOrderMediaOverlay(isMediaOverlay: Boolean) {
        mBinding.surfaceViewFpv.setZOrderMediaOverlay(isMediaOverlay)
    }

    //endregion
    //region Helpers
    private fun setViewDimensions() {
        viewWidth = measuredWidth
        viewHeight = measuredHeight
    }

    private fun delayCalculator() {
        //后面补充
    }

    private fun updateCameraName(cameraName: String) {
        Log.e(TAG, "updateCameraName: $cameraName")

    }
    private fun updateAssistantDisplay(show: Boolean){
        Log.e(TAG, "updateAssistantDisplay: $show")
        if (show){
            mBinding.boxContainer.visibility = View.GONE
            mBinding.widgetFpvContainer.visibility = View.VISIBLE
        }else{
            mBinding.boxContainer.visibility = View.VISIBLE
            mBinding.widgetFpvContainer.visibility = View.GONE
        }
    }
    private fun updateGridLineVisibility() {
        mBinding.viewGridLine.visibility =
            if (isGridLinesEnabled && widgetModel.getCameraIndex() == ComponentIndexType.FPV) View.VISIBLE else View.GONE
    }


    @SuppressLint("Recycle", "CustomViewStyleable")
    private fun initAttributes(context: Context, attrs: AttributeSet) {
        context.obtainStyledAttributes(attrs, R.styleable.FPVWidget).use { typedArray ->
            if (!isInEditMode) {
                typedArray.getBooleanAndUse(R.styleable.FPVWidget_uxsdk_gridLinesEnabled, true) {
                    isGridLinesEnabled = it
                }
                typedArray.getBooleanAndUse(R.styleable.FPVWidget_uxsdk_centerPointEnabled, true) {
                    isCenterPointEnabled = it
                }
            }
            typedArray.getIntegerAndUse(R.styleable.FPVWidget_uxsdk_gridLineType) {
                mBinding.viewGridLine.type = GridLineView.GridLineType.find(it)
            }
            typedArray.getColorAndUse(R.styleable.FPVWidget_uxsdk_gridLineColor) {
                mBinding.viewGridLine.lineColor = it
            }
            typedArray.getFloatAndUse(R.styleable.FPVWidget_uxsdk_gridLineWidth) {
                mBinding.viewGridLine.lineWidth = it
            }
            typedArray.getIntegerAndUse(R.styleable.FPVWidget_uxsdk_gridLineNumber) {
                mBinding.viewGridLine.numberOfLines = it
            }
            typedArray.getIntegerAndUse(R.styleable.FPVWidget_uxsdk_centerPointType) {
                mBinding.viewCenterPoint.type = CenterPointView.CenterPointType.find(it)
            }
            typedArray.getColorAndUse(R.styleable.FPVWidget_uxsdk_centerPointColor) {
                mBinding.viewCenterPoint.color = it
            }
            typedArray.getResourceIdAndUse(R.styleable.FPVWidget_uxsdk_onStateChange) {
                fpvStateChangeResourceId = it
            }
            typedArray.getDimensionAndUse(R.styleable.FPVWidget_uxsdk_currentDirectionMargin){
                currentDirectionMargin = it.toInt()
            }
            typedArray.getDimensionAndUse(R.styleable.FPVWidget_uxsdk_verticalMargin){
                verticalMargin = it.toInt()
            }
            typedArray.getDimensionAndUse(R.styleable.FPVWidget_uxsdk_horizontalMargin){
                horizontalMargin = it.toInt()
            }
        }
    }

    private fun updateCameraStream() {
        removeSurfaceBinding()
        surface?.let {
            widgetModel.putCameraStreamSurface(
                it,
                width,
                height,
                ICameraStreamManager.ScaleType.CENTER_INSIDE
            )
        }
    }

    private fun removeSurfaceBinding() {
        if (width <= 0 || height <= 0 || surface == null) {
            if (surface != null) {
                widgetModel.removeCameraStreamSurface(surface!!)
            }
        }
    }
}