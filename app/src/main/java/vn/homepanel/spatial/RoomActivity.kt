package vn.homepanel.spatial

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.*
import com.meta.spatial.mruk.*
import com.meta.spatial.physics.PhysicsFeature
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.PointerEventType
import com.meta.spatial.toolkit.*
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.vr.VrInputSystemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import vn.homepanel.*
import vn.homepanel.R
import com.meta.spatial.core.Color4
import vn.homepanel.storage.SpatialBinding
import vn.homepanel.ha.*
import vn.homepanel.ui.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap

private enum class Mode { HOME, EXPLORE, AIM, CONFIRM, CONTROL }
private data class RoomUi(val mode: Mode = Mode.HOME, val status: String = "", val target: String = "", val targetBinding: String? = null, val distance: Float = 2f, val surface: Boolean = true, val resolved: Int = 0, val total: Int = 0, val canSave: Boolean = false, val handsTracked: Boolean = false, val countdown: Int = 0, val width: Float = .5f, val height: Float = .5f, val aimHint: Int = R.string.frame_aim, val saving: Boolean = false, val loading: Boolean = false, val settingUp: Boolean = false, val roomKind: RoomKind = RoomKind.UNKNOWN, val warning: String? = null, val controlBinding: String? = null)
private data class Placement(val room: String, val anchor: String, val local: Pose, val world: Pose, val device: String, val entity: String, val label: String, val server: String, val width: Float, val height: Float)

class RoomActivity : AppSystemActivity() {
    private lateinit var mruk: MRUKFeature
    private val store get() = (application as HomePanelApp).store
    private val ui = MutableStateFlow(RoomUi())
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private var home: Entity? = null; private var controls: Entity? = null; private var hint: Entity? = null; private var navigation: Entity? = null; private var preview: DeviceFrame? = null
    private val markers = mutableMapOf<String, DeviceFrame>()
    private var headPose: Pose? = null
    private var aimPose: Pose? = null
    private val placementPanel = PlacementPanelFollow()
    private val controlPanel = PlacementPanelFollow(Vector3(0f, -.15f, .85f))
    private val navigationPanel = PlacementPanelFollow(Vector3(-.42f, -.38f, .90f))
    private val actionsFocus = ContextualActionsFocus()
    private var actionsPose: Pose? = null
    private var actionsHead: Vector3? = null
    private var candidate: Placement? = null
    private var frozen: Placement? = null
    private var sceneReady = false
    private var loadingRoom = false
    private var capturingRoom = false
    private var roomLoadGeneration = 0
    private var captureAfterPermission = false
    private val roomHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var initialized = false
    private var controlBindingId: String? = null
    private var openingBindingId: String? = null
    private var lastResolution: Pair<Int, Int>? = null
    private val selectionInput = SelectionInput()
    private val panelPointers = ConcurrentHashMap.newKeySet<Pair<Entity, Entity>>()
    private val pendingPanelListeners = mutableSetOf<Entity>()
    private var aimSince = 0L
    private var freezeAt = 0L
    private var lastFrameUi = 0L
    private var aimDevice: String? = null; private var aimEntity: String? = null

    override fun registerFeatures(): List<SpatialFeature> {
        mruk = MRUKFeature(this, systemManager)
        return listOf(VRFeature(this, inputSystemType = VrInputSystemType.INTERACTION_SDK), ComposeFeature(), PhysicsFeature(spatial), mruk)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!(application as vn.homepanel.HomePanelApp).entitlement.allowed) { finish(); return }
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
        systemManager.registerSystem(object : SystemBase() { override fun execute() { frame() } })
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueue { if (takePlaceRequest()) beginAim() else mode(entryMode()) }
    }
    override fun onSceneReady() {
        super.onSceneReady()
        // A recreated spatial scene needs fresh panel placement from its first head pose.
        initialized = false
        headPose = null
        aimPose = null
        actionsFocus.reset()
        actionsPose = null
        lastResolution = null
        // Entities of a previous scene are gone; markers are recreated from the bindings.
        markers.clear()
        android.util.Log.i("HomePanelSpatial", "Scene ready; placement requested=${intent.getBooleanExtra(EXTRA_PLACE, false)}")
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(true)
        home = Entity.create(listOf(Panel(R.id.home_panel), Transform(), Visible(false)))
        controls = Entity.create(listOf(Panel(R.id.device_panel), Transform(), Visible(false)))
        hint = Entity.create(listOf(Panel(R.id.target_panel), Transform(), Visible(false)))
        navigation = Entity.create(listOf(Panel(R.id.room_navigation), Transform(), Visible(false)))
        pendingPanelListeners.addAll(listOfNotNull(home, controls, hint, navigation))
        preview = DeviceFrame()
        sceneReady = true
        runOnUiThread { ensureRoom(false) }
    }
    override fun registerPanels(): List<PanelRegistration> = listOf(
        panel(R.id.home_panel, 1.08f, .76f) {
            val room by ui.collectAsState()
            val state by store.state.collectAsState()
            val suggestions = remember(room.roomKind, state.catalog, state.bindings) { suggestedForRoom(room.roomKind, state.catalog, state.bindings) }
            HomeScreen(store, {}, inRoom = true, onPlace = { enqueue { beginAim() } }, suggestions = suggestions, onSuggest = { device -> store.select(device.key, primaryEntity(device)?.id); enqueue { beginAim() } }, roomStatus = room.status + " · " + stringResource(if (room.handsTracked) R.string.hands_ready else R.string.hands_missing) + if (room.total > 0) stringResource(R.string.resolved_placements, room.resolved, room.total) else "", onScan = { ensureRoom(true) }, onExit = { exitToWindow() }, onExplore = { enqueue { mode(Mode.EXPLORE) } })
        },
        panel(R.id.device_panel, .58f, .90f) {
            val room by ui.collectAsState()
            val state by store.state.collectAsState()
            var fullControls by remember(state.selectedDevice, room.mode) { mutableStateOf(false) }
            HomeTheme {
                Surface(Modifier.fillMaxSize()) {
                    val isPlacement = room.mode == Mode.AIM || room.mode == Mode.CONFIRM
                    val scroll = rememberScrollState()
                    // Keep placement buttons out of a scroll container: ISDK direct touch can
                    // lose presses on scrollable Compose panels. Other device details still scroll.
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).then(if(!isPlacement && fullControls) Modifier.verticalScroll(scroll) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when(room.mode) {
                            Mode.AIM, Mode.CONFIRM -> {
                                PlacementForm(
                                    PlacementFormState(device=state.catalog.devices.find { it.key == state.selectedDevice }?.let { placementLabel(it,state.selectedEntity.orEmpty()) }.orEmpty(), status=room.status, confirming=room.mode==Mode.CONFIRM, ready=room.canSave, loading=room.loading, settingUp=room.settingUp, saving=room.saving, countdown=room.countdown, distance=room.distance, width=room.width, height=room.height, surface=room.surface, hint=room.aimHint, warning=room.warning),
                                    onAdjust={ value -> ui.update { it.copy(distance=value.distance,width=value.width,height=value.height,surface=value.surface) } },
                                    onScan={ ensureRoom(true) },
                                    onReload={ ensureRoom(false) },
                                    onFreeze={ enqueue { freezeAt=SystemClock.uptimeMillis()+3000; ui.update { it.copy(countdown=3) } } },
                                    onCancelCountdown={ enqueue { freezeAt=0L; ui.update { it.copy(countdown=0) } } },
                                    onSave={ enqueue { savePlacement() } },
                                    onAimAgain={ enqueue { beginAim() } },
                                )
                            }
                            else -> {
                                state.catalog.devices.find { it.key == state.selectedDevice }?.let {
                                    if (fullControls) {
                                        TextButton(onClick = { fullControls = false }) { Text(stringResource(R.string.quick_controls)) }
                                        DeviceDetail(store, it, onPlace = { enqueue { beginAim() } })
                                    } else QuickControls(store, it, onDetails = { fullControls = true }, onPlace = { enqueue { beginAim() } }, placementLabel = stringResource(R.string.place_object), large = true)
                                }
                                room.controlBinding?.takeIf { room.mode == Mode.CONTROL }?.let { id ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(onClick = { enqueue { beginAim() } }, shape = Rounded, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.move_frame)) }
                                        OutlinedButton(onClick = { enqueue { resizeBinding(id) } }, shape = Rounded, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.resize_frame)) }
                                    }
                                }
                                state.message?.let { Text(it, fontSize = 13.sp, maxLines = 3) }
                            }
                        }
                    }
                        Text(stringResource(if (room.handsTracked) R.string.hands_ready else R.string.hands_missing), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { enqueue { closeControls() } }) { Text(stringResource(if(room.mode == Mode.CONTROL) R.string.close_controls else R.string.explore)) }
                            TextButton(onClick = { enqueue { mode(Mode.HOME) } }) { Text(stringResource(R.string.list)) }
                            TextButton(onClick = { exitToWindow() }) { Text(stringResource(R.string.return_window)) }
                        }
                    }
                }
            }
        },
        panel(R.id.target_panel, .34f, .14f) {
            val room by ui.collectAsState()
            HomeTheme {
                room.targetBinding?.let { id -> SpatialActionIcons(store,id,onDetails={ enqueue { openBinding(id) } }) }
            }
        },
        panel(R.id.room_navigation, .08f, .14f) {
            val room by ui.collectAsState()
            HomeTheme {
                Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    SpatialIconButton(ActionGlyph.LIST,stringResource(R.string.list)) { enqueue { mode(Mode.HOME) } }
                    Spacer(Modifier.height(10.dp))
                    SpatialIconButton(ActionGlyph.DASHBOARD,stringResource(R.string.dashboard)) { exitToWindow() }
                    if(room.total>room.resolved) Text("!",color=MaterialTheme.colorScheme.secondary,fontSize=14.sp)
                }
            }
        },
    )
    private fun panel(id: Int, width: Float, height: Float, content: @Composable () -> Unit) = ComposeViewPanelRegistration(id, composeViewCreator = { _, ctx -> ComposeView(ctx).apply { setContent { LocalizedContent(content) } } }, settingsCreator = { UIPanelSettings(shape = QuadShapeOptions(width = width, height = height), display = DpPerMeterDisplayOptions(dpPerMeter = 1000f), style = PanelStyleOptions(themeResourceId = R.style.PanelTheme)) })
    private fun text(id: Int) = AppLanguage.text(this,id)
    private fun enqueue(command: () -> Unit) { commands.add(command) }
    private fun watchPanelInput(panel: Entity): Boolean {
        val future = systemManager.findSystem<SceneObjectSystem>().getSceneObject(panel) ?: return false
        future.thenAccept { obj ->
            obj?.addInputListener(object : InputListener {
                override fun onPointerEvent(receiver: SceneObject, hitInfo: HitInfo, type: Int, sourceOfInput: Entity, scrollInfo: Vector2, semanticType: Int) {
                    val key = panel to sourceOfInput
                    when (type) {
                        PointerEventType.Hover.id, PointerEventType.Select.id -> panelPointers.add(key)
                        PointerEventType.Unhover.id, PointerEventType.Cancel.id -> panelPointers.remove(key)
                    }
                    if(type == PointerEventType.Select.id) android.util.Log.d("HomePanelSpatial", "Panel press: ${if(panel == controls) "editor" else "navigation"}")
                }
            })
        }
        return true
    }
    private fun lookingAtControls(head: Pose): Boolean {
        val panel = controls ?: return false
        return lookingAtPanel(head,getAbsoluteTransform(panel),.58f,.90f,.05f)
    }
    private fun ensureRoom(capture: Boolean) {
        android.util.Log.i("HomePanelSpatial", "Room action: capture=$capture, ready=$sceneReady, loading=$loadingRoom, capturing=$capturingRoom")
        if (!sceneReady || (loadingRoom && (!capturingRoom || capture))) return
        if (checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            captureAfterPermission = capture
            ui.update { it.copy(status = text(R.string.scene_permission), loading=false) }
            requestPermissions(arrayOf(PERMISSION),41)
            return
        }
        loadingRoom = true
        capturingRoom = capture
        val generation = ++roomLoadGeneration
        ui.update { it.copy(status=text(if(capture) R.string.room_scanning else R.string.room_loading), loading=true, settingUp=capture, canSave=false) }
        fun complete(success: Boolean, status: Int) {
            runOnUiThread {
                // A load that finishes after the timeout still corrects the status, unless a newer load is running.
                if (!isDestroyed && (generation == roomLoadGeneration || (success && !loadingRoom))) {
                    loadingRoom=false
                    capturingRoom=false
                    ui.update { it.copy(loading=false,settingUp=false,status=text(status)) }
                    android.util.Log.i("HomePanelSpatial", "Room load finished: success=$success, rooms=${mruk.rooms.size}")
                }
            }
        }
        val load = {
            capturingRoom=false
            ui.update { it.copy(loading=true,settingUp=false,status=text(R.string.room_loading)) }
            roomHandler.postDelayed({
                if (generation == roomLoadGeneration && loadingRoom) {
                    roomLoadGeneration++
                    loadingRoom=false
                    ui.update { it.copy(loading=false,settingUp=false,status=text(R.string.room_timeout)) }
                    android.util.Log.w("HomePanelSpatial", "Room load timed out")
                }
            },12_000)
            try {
                // Room capture is explicitly requested by its own button. The SDK default
                // can open system setup inside this timed load, which invalidates its result.
                mruk.loadSceneFromDevice(requestSceneCaptureIfNoDataFound=false).whenComplete { result,error ->
                    val success = error==null && result==MRUKLoadDeviceResult.SUCCESS && mruk.rooms.isNotEmpty()
                    android.util.Log.i("HomePanelSpatial", "Room discovery: result=$result, error=${error?.javaClass?.simpleName}, rooms=${mruk.rooms.size}")
                    complete(success, if(success) R.string.room_ready else R.string.room_failed)
                }
            } catch (_: Exception) { complete(false,R.string.room_failed) }
        }
        if (capture) {
            try { mruk.requestSceneCapture().whenComplete { _,error ->
                android.util.Log.i("HomePanelSpatial", "Room setup returned: error=${error?.javaClass?.simpleName}")
                runOnUiThread { if (generation==roomLoadGeneration) { if(error==null) load() else complete(false,R.string.scan_failed) } }
            } } catch (_: Exception) { complete(false,R.string.scan_failed) }
        } else load()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 41) { if(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) ensureRoom(captureAfterPermission) else ui.update { it.copy(status = text(R.string.scene_permission)) } }
    }
    private fun mode(value: Mode) {
        android.util.Log.i("HomePanelSpatial", "Mode: $value")
        ui.update { it.copy(mode = value, target = "", targetBinding = null, canSave = false, countdown = 0, saving = false, warning = null, controlBinding = if (value == Mode.CONTROL) it.controlBinding else null) }; actionsFocus.reset(); actionsPose=null; actionsHead=null; selectionInput.reset(); panelPointers.clear(); focusedId = null; freezeAt = 0L
        openingBindingId = null
        if (value != Mode.CONTROL) controlBindingId = null
        if(value != Mode.AIM && value != Mode.CONFIRM) { candidate = null; frozen = null; preview?.hide() }
        home?.setComponent(Visible(value == Mode.HOME)); controls?.setComponent(Visible(value in listOf(Mode.AIM, Mode.CONFIRM, Mode.CONTROL))); hint?.setComponent(Visible(false)); navigation?.setComponent(Visible(value==Mode.EXPLORE))
        headPose?.let { pose ->
            if(value == Mode.HOME) home?.setComponent(Transform(panelPose(pose, Vector3(0f, -.05f, 1.2f))))
            if(value in listOf(Mode.AIM, Mode.CONFIRM)) controls?.setComponent(Transform(placementPanel.reset(pose,SystemClock.uptimeMillis())))
            if(value == Mode.CONTROL) controls?.setComponent(Transform(controlPanel.reset(pose,SystemClock.uptimeMillis())))
            if(value == Mode.EXPLORE) navigation?.setComponent(Transform(navigationPanel.reset(pose,SystemClock.uptimeMillis())))
        }
    }
    private fun entryMode() = if (store.state.value.bindings.isEmpty()) Mode.HOME else Mode.EXPLORE
    private fun closeControls() {
        mode(Mode.EXPLORE)
    }
    private fun beginAim() {
        val state = store.state.value
        if(state.selectedDevice == null || state.selectedEntity == null) { mode(Mode.HOME); runOnUiThread { store.notify(text(R.string.choose_function_first)) }; return }
        aimDevice = state.selectedDevice; aimEntity = state.selectedEntity
        val previous = state.bindings.find { it.entityId == state.selectedEntity }
        ui.update { it.copy(width = previous?.width ?: .5f, height = previous?.height ?: .5f) }
        runOnUiThread { store.notify(null) }
        aimPose = headPose?.copy()
        freezeAt = 0L; mode(Mode.AIM); aimSince = SystemClock.uptimeMillis(); candidate = null; frozen = null
    }
    /** A placement request is consumed once, so a recreated scene does not start aiming again after a save. */
    private fun takePlaceRequest(): Boolean = intent.getBooleanExtra(EXTRA_PLACE, false).also { if (it) intent.removeExtra(EXTRA_PLACE) }
    private fun freeze() {
        if(ui.value.mode != Mode.AIM) return
        frozen = candidate ?: run { ui.update { it.copy(status = text(R.string.frame_not_ready)) }; return }
        mode(Mode.CONFIRM)
        ui.update { it.copy(canSave = true) }
    }
    /** Resize keeps the saved position: the existing placement goes straight to confirmation with its size editable. */
    private fun resizeBinding(id: String) {
        val b = store.state.value.bindings.find { it.id == id } ?: return
        val room = mruk.rooms.find { it.anchor.uuid.toString() == b.roomId }
        val anchor = room?.anchors?.find { it.tryGetComponent<MRUKAnchor>()?.uuid?.toString() == b.anchorId }
        if (anchor == null) { runOnUiThread { store.notify(text(R.string.room_changed)) }; return }
        aimDevice = b.deviceKey; aimEntity = b.entityId
        val local = b.localPose()
        frozen = Placement(b.roomId, b.anchorId, local, getAbsoluteTransform(anchor) * local, b.deviceKey, b.entityId, b.label, b.serverKey, b.width, b.height)
        mode(Mode.CONFIRM)
        ui.update { it.copy(width = b.width, height = b.height, canSave = true) }
    }
    private fun savePlacement() {
        val p = frozen ?: return
        if (ui.value.saving) return
        val room = mruk.rooms.find { it.anchor.uuid.toString() == p.room }
        if(room == null || room.anchors.none { it.tryGetComponent<MRUKAnchor>()?.uuid?.toString() == p.anchor }) {
            ui.update { it.copy(canSave = false, status = text(R.string.room_changed)) }; return
        }
        if (store.state.value.serverKey != p.server) { mode(Mode.HOME); return }
        val binding = SpatialBinding(serverKey = p.server, deviceKey = p.device, entityId = p.entity, roomId = p.room, anchorId = p.anchor, x = p.local.t.x, y = p.local.t.y, z = p.local.t.z, label = p.label, width = ui.value.width, height = ui.value.height, qw = p.local.q.w, qx = p.local.q.x, qy = p.local.q.y, qz = p.local.q.z)
        ui.update { it.copy(saving = true) }
        runOnUiThread {
            val saved = store.saveBinding(binding)
            enqueue {
                if (ui.value.mode == Mode.CONFIRM && frozen === p) {
                    if (saved) {
                        mode(Mode.EXPLORE)
                        actionsFocus.show(binding.id,SystemClock.uptimeMillis())
                        runOnUiThread { store.notify(null) }
                    }
                    else ui.update { it.copy(saving = false, status = text(R.string.placement_failed)) }
                }
            }
        }
    }
    private var focusedId: String? = null
    private var resolvedReported: Set<String>? = null
    private var occluded = emptySet<String>()
    private var lastOcclusion = 0L
    private fun openBinding(id: String) {
        if (openingBindingId != null) return
        val binding = store.state.value.bindings.find { it.id == id } ?: return
        val device = store.state.value.catalog.devices.find { it.key == binding.deviceKey }
        if(device == null || device.entities.none { it.id == binding.entityId }) { runOnUiThread { store.notify(text(R.string.device_gone)) }; return }
        openingBindingId = id
        runOnUiThread {
            val selected = store.selectBinding(id)
            enqueue {
                if (openingBindingId == id) {
                    openingBindingId = null
                    val current = store.state.value
                    if (selected && current.serverKey == binding.serverKey && current.bindings.any { it.id == id } && current.selectedDevice == binding.deviceKey && current.selectedEntity == binding.entityId) {
                        ui.update { it.copy(controlBinding = id) }
                        mode(Mode.CONTROL)
                        controlBindingId = id
                        android.util.Log.i("HomePanelSpatial", "Opened controls for saved placement")
                    }
                }
            }
        }
    }
    private fun frame() {
        if(!sceneReady) return
        pendingPanelListeners.toList().forEach { if (watchPanelInput(it)) pendingPanelListeners.remove(it) }
        val avatar = systemManager.tryFindSystem<PlayerBodyAttachmentSystem>()?.tryGetLocalPlayerAvatarBody()
        val head = avatar?.head?.tryGetComponent<Transform>()?.let { getAbsoluteTransform(avatar.head) }
        if(avatar == null || head == null || head == Pose()) { selectionInput.reset(); actionsFocus.reset(); hint?.setComponent(Visible(false)); freezeAt = 0L; return }
        headPose = head
        if(!initialized) { initialized = true; if(takePlaceRequest()) beginAim() else mode(entryMode()) }
        while(true) { val command = commands.poll() ?: break; command() }
        val hands = listOf(InputSide.LEFT to avatar.leftHand, InputSide.RIGHT to avatar.rightHand).mapNotNull { (side, hand) ->
            hand.tryGetComponent<Controller>()?.let { controller ->
                val tracked = controller.isActive && hand.tryGetComponent<Transform>() != null
                SelectionSample(side, controller.type, tracked, controller.buttonState, tracked && getAbsoluteTransform(hand).t.y > head.t.y - .45f)
            }
        }
        if (hands.none { it.active }) panelPointers.clear()
        val activePanels = when (ui.value.mode) {
            Mode.HOME -> listOfNotNull(home)
            Mode.EXPLORE -> listOfNotNull(hint,navigation)
            else -> listOfNotNull(controls)
        }
        val isPlacement = ui.value.mode == Mode.AIM || ui.value.mode == Mode.CONFIRM
        val panelBusy = panelPointers.any { it.first in activePanels } || ((isPlacement || ui.value.mode == Mode.CONTROL) && hands.any { it.raised } && lookingAtControls(head))
        val now = SystemClock.uptimeMillis()
        if (isPlacement) controls?.setComponent(Transform(placementPanel.update(head, panelBusy, now)))
        if (ui.value.mode == Mode.CONTROL) controls?.setComponent(Transform(controlPanel.update(head, panelBusy, now)))
        if (ui.value.mode == Mode.EXPLORE) navigation?.setComponent(Transform(navigationPanel.update(head,panelPointers.any { it.first==navigation },now)))
        val selected = selectionInput.update(hands, blocked = panelBusy)
        if(listOf(avatar.leftHand, avatar.rightHand).any { hand -> hand.tryGetComponent<Controller>()?.let { c -> c.isActive && c.type == ControllerType.CONTROLLER && (c.isPressed(ButtonBits.ButtonMenu) || c.isPressed(ButtonBits.ButtonB)) } == true }) mode(Mode.HOME)
        val room = mruk.getCurrentRoom()
        val state = store.state.value
        // Placements in every scanned room stay visible, e.g. a hallway lamp seen through a doorway.
        val anchors = mruk.rooms.flatMap { r -> r.anchors.mapNotNull { e -> e.tryGetComponent<MRUKAnchor>()?.let { "${r.anchor.uuid}/${it.uuid}" to e } } }.toMap()
        val resolved = state.bindings.mapNotNull { b -> anchors["${b.roomId}/${b.anchorId}"]?.let { e -> b to (getAbsoluteTransform(e) * b.localPose()) } }
        val valid = resolved.map { it.first.id }.toSet()
        if (resolvedReported != valid) { resolvedReported = valid; runOnUiThread { store.reportResolvedPlacements(valid) } }
        val forward = head.q * Vector3(0f,0f,1f)
        var lookedTarget: String? = null
        if (ui.value.mode == Mode.EXPLORE) {
            // Frames hidden behind furniture are not targets; checked a few times a second.
            if (now - lastOcclusion > 250) {
                lastOcclusion = now
                occluded = if (room == null) emptySet() else resolved.filter { (_, pose) ->
                    val delta = pose.t - head.t; val distance = delta.length()
                    distance > .3f && mruk.raycastRoom(room.anchor.uuid, head.t, delta.normalize(), distance, SurfaceType.VOLUME)?.let { it.hitDistance < distance - .12f } == true
                }.map { it.first.id }.toSet()
            }
            lookedTarget = chooseFrameTarget(head, resolved.filter { it.first.id !in occluded }.map { TargetFrame(it.first.id, it.second, it.first.width, it.first.height) }, focusedId)
            focusedId = lookedTarget
        }
        // A removed/unresolved anchor cannot stay selected just because a pointer hovers a panel.
        if (focusedId !in valid) focusedId = null
        markers.keys.toList().filter { it !in valid }.forEach { markers.remove(it)?.destroy() }
        resolved.forEach { (b, pose) ->
            val outline = markers.getOrPut(b.id) { DeviceFrame() }
            val selectedMarker = (ui.value.mode == Mode.EXPLORE && b.id == focusedId) || (ui.value.mode == Mode.CONTROL && b.entityId == state.selectedEntity)
            if ((ui.value.mode == Mode.EXPLORE || selectedMarker) && markerVisible(head.t.point(),forward.point(),pose.t.point())) {
                val entity = state.catalog.entities[b.entityId]
                val tone = if (!state.connected || entity?.available != true) MarkerTone.UNAVAILABLE else if (entity.state in setOf("off","closed")) MarkerTone.IDLE else MarkerTone.ACTIVE
                outline.show(pose, if(selectedMarker) .055f else .035f, if(selectedMarker) .055f else .035f, tone=tone)
            } else outline.hide()
        }
        if (now-lastFrameUi > 500) {
            lastFrameUi = now
            val kind = room?.anchors?.flatMap { e -> e.tryGetComponent<MRUKAnchor>()?.let { a -> (0 until a.labelsCount).mapNotNull { i -> runCatching { a.labels.get(i) }.getOrNull() } }.orEmpty() }?.let(::roomKindFromLabels) ?: RoomKind.UNKNOWN
            if (ui.value.roomKind != kind) ui.update { it.copy(roomKind = kind) }
            ui.update { it.copy(resolved = resolved.size, total = state.bindings.size, handsTracked = hands.any { sample -> sample.active && sample.type == ControllerType.HAND }, countdown = if (freezeAt == 0L) 0 else ((freezeAt - now).coerceAtLeast(0) + 999).toInt() / 1000) }
            val resolution = resolved.size to state.bindings.size
            if (lastResolution != resolution) { lastResolution = resolution; android.util.Log.i("HomePanelSpatial", "Resolved placements: ${resolution.first}/${resolution.second}") }
        }
        when(ui.value.mode) {
            Mode.AIM -> {
                // Looking at a button must not pull the frame away from the chosen appliance.
                if (!panelBusy || aimPose == null) aimPose = head.copy()
                val aim = aimPose ?: head
                val direction = aim.q * Vector3(0f,0f,1f)
                // The hit is used to choose the anchor even when the frame floats at a manual distance.
                val hit = if(room != null) mruk.raycastRoom(room.anchor.uuid, aim.t, direction, 8f, SurfaceType.PLANE_VOLUME) else null
                val snapped = ui.value.surface && hit != null
                val worldPose = placementPose(aim, ui.value.distance, hit?.hitPosition?.takeIf { snapped }, hit?.hitNormal?.takeIf { snapped }, ui.value.height)
                val roomAnchors = room?.anchors?.mapNotNull { e -> e.tryGetComponent<MRUKAnchor>()?.let { Triple(it.uuid.toString(), e, getAbsoluteTransform(e).t) } }.orEmpty()
                val anchorId = placementAnchor(hit?.sceneAnchorUuid?.toString(), worldPose.t, roomAnchors.map { it.first to it.third })
                val anchor = roomAnchors.find { it.first == anchorId }?.second
                val device = state.catalog.devices.find { it.key == aimDevice }
                candidate = if(room != null && anchor != null && anchorId != null && device != null && aimEntity != null) {
                    val local = getAbsoluteTransform(anchor).inverse() * worldPose
                    Placement(room.anchor.uuid.toString(), anchorId, local, worldPose, device.key, aimEntity!!, placementLabel(device,aimEntity!!), state.serverKey, ui.value.width, ui.value.height)
                } else null
                preview?.show(worldPose, ui.value.width, ui.value.height, ready = candidate != null)
                val help = if (ui.value.surface && !snapped) R.string.frame_no_surface else R.string.frame_aim
                val overlap = resolved.firstOrNull { (b, pose) -> b.entityId != aimEntity && frameOverlap(worldPose, ui.value.width, ui.value.height, pose, b.width, b.height) > .5f }?.first
                val area = device?.area.orEmpty()
                val warning = when {
                    overlap != null -> AppLanguage.text(this, R.string.frame_overlaps, overlap.label)
                    device != null && areaMismatch(ui.value.roomKind, areaKind(area)) -> AppLanguage.text(this, R.string.area_mismatch, device.name, area, text(if (ui.value.roomKind == RoomKind.BEDROOM) R.string.room_kind_bedroom else R.string.room_kind_living))
                    else -> null
                }
                if (ui.value.warning != warning) ui.update { it.copy(warning = warning) }
                if(ui.value.canSave != (candidate != null) || ui.value.aimHint != help) ui.update { it.copy(canSave = candidate != null, aimHint = help) }
                if((selected && now - aimSince > 700) || (freezeAt != 0L && now >= freezeAt)) { freezeAt = 0L; freeze() }
            }
            Mode.CONFIRM -> {
                frozen?.let { p ->
                    val anchor = anchors["${p.room}/${p.anchor}"]
                    val localized = anchor != null
                    if(localized) preview?.show(getAbsoluteTransform(anchor!!) * p.local,ui.value.width,ui.value.height) else preview?.hide()
                    if(ui.value.canSave != localized) ui.update { it.copy(canSave = localized) }
                }
            }
            Mode.EXPLORE -> {
                val openableTarget = lookedTarget?.takeIf { id ->
                    val binding = state.bindings.find { it.id == id }
                    binding != null && state.catalog.devices.any { it.key == binding.deviceKey && it.entities.any { e -> e.id == binding.entityId } }
                }
                if(actionsFocus.target != null && actionsFocus.target !in valid) actionsFocus.reset()
                val interacting = actionsFocus.target!=null && (panelPointers.any { it.first==hint } || actionsPose?.let { lookingAtPanel(head,it,.34f,.14f) }==true)
                val shown=actionsFocus.update(openableTarget,interacting,now)
                val focused=resolved.find { it.first.id==shown }
                val changed=ui.value.targetBinding!=shown
                if(changed) {
                    ui.update { it.copy(target=focused?.first?.label.orEmpty(),targetBinding=shown) }
                    android.util.Log.d("HomePanelSpatial","Action icons: ${if(shown==null) "hidden" else "visible"}")
                    if(shown!=null) runOnUiThread { store.notify(null) }
                }
                if(focused!=null) {
                    if(changed || actionsPose==null || (!interacting && actionsHead?.distanceTo(head.t)?.let { it>.25f }==true)) {
                        actionsPose=contextualActionsPose(head,focused.second.t)
                        actionsHead=head.t.copy()
                        hint?.setComponent(Transform(actionsPose!!))
                    }
                    hint?.setComponent(Visible(true))
                } else {
                    hint?.setComponent(Visible(false))
                    actionsPose=null
                    panelPointers.removeAll { it.first==hint }
                }
            }
            else -> Unit
        }
    }
    private fun Vector3.point() = Point3(x,y,z)
    override fun onRecenter(isUserInitiated: Boolean) { super.onRecenter(isUserInitiated); enqueue { mode(ui.value.mode) } }
    // Meta's hybrid-app return pattern targets the system HOME category, which our
    // non-exported VR activity does not declare. Lint matches only ACTION_MAIN here.
    // https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview/
    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    private fun exitToWindow() {
        val pending = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("extra_launch_in_home_pending_intent",pending)); finish()
    }
    override fun onPause() { enqueue { selectionInput.reset(); actionsFocus.reset(); panelPointers.clear(); freezeAt = 0L }; super.onPause() }
    override fun onSpatialShutdown() { resolvedReported = null; store.reportResolvedPlacements(null); roomLoadGeneration++; roomHandler.removeCallbacksAndMessages(null); loadingRoom=false; capturingRoom=false; sceneReady = false; commands.clear(); panelPointers.clear(); pendingPanelListeners.clear(); selectionInput.reset(); super.onSpatialShutdown() }
    companion object { const val EXTRA_PLACE = "vn.homepanel.PLACE_DEVICE"; private const val PERMISSION = "com.oculus.permission.USE_SCENE" }
}
