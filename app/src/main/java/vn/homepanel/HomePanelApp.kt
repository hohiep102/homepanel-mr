package vn.homepanel

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.homepanel.ha.*
import vn.homepanel.storage.*
import vn.homepanel.auth.*

data class AppState(val catalog: Catalog = Catalog(), val status: String = "", val connected: Boolean = false, val demo: Boolean = false, val serverKey: String = "", val serverUrl: String = "", val selectedDevice: String? = null, val selectedEntity: String? = null, val message: String? = null, val busy: Set<String> = emptySet(), val bindings: List<SpatialBinding> = emptyList())
class HomePanelApp : Application() {
    val entitlement by lazy { EntitlementGate(this) }
    val store by lazy { AppStore(this) }
    override fun onCreate() { super.onCreate(); AppLanguage.load(this); entitlement.check() }
}
class AppStore(private val app: Application) {
    private fun text(id: Int, vararg args: Any) = AppLanguage.text(app,id,*args)
    fun changeLanguage(tag: String) {
        AppLanguage.select(app,tag)
        if (state.value.demo) {
            val names = Demo.catalog(tag == "vi")
            mutableState.update { old ->
                val entities = old.catalog.entities.mapValues { (id,e) -> e.copy(attributes = org.json.JSONObject(e.attributes.toString()).put("friendly_name", names.entities[id]?.name ?: e.name)) }
                old.copy(catalog = old.catalog.copy(entities = entities, areas = names.areas, devices = old.catalog.devices.map { device ->
                    val translated = names.devices.find { it.key == device.key } ?: device
                    device.copy(name = translated.name, area = translated.area, entities = device.entities.map { entities.getValue(it.id) })
                }), message = null, status = text(R.string.demo_status))
            }
        } else mutableState.update { it.copy(message = null, status = text(if (it.connected) R.string.connected else R.string.not_connected)) }
    }
    private val credentials = CredentialStore(app)
    private val bindingStore = BindingStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connection: Job? = null
    private var session: HaSession? = null
    private var generation = 0
    private var allBindings = emptyList<SpatialBinding>()
    private var bindingsReadable = true
    private val mutableState = MutableStateFlow(AppState(status = text(R.string.not_connected)))
    val state = mutableState.asStateFlow()
    private val authClient=HaAuthClient()
    val login=BrowserLogin(scope,authClient,{ address,auth,remember -> connectCredentials(address,auth,remember,cancelLogin=false) })
    private var activeAuth: Pair<ServerAddress,HaCredentials>?=null
    init {
        runCatching { allBindings = bindingStore.load() }.onFailure { bindingsReadable = false; notify(text(R.string.bindings_unreadable)) }
        runCatching { credentials.loadAuth() }.onSuccess { it?.let { (url, auth) -> connectCredentials(ServerAddress.parse(url),auth,true,restoring=true) } }.onFailure { notify(text(R.string.auth_storage_error)) }
    }
    fun notify(message: String?) { mutableState.update { it.copy(message = message) } }
    fun select(device: String, entity: String? = null) {
        val found = state.value.catalog.devices.find { it.key == device } ?: return
        val selected = entity?.takeIf { id -> found.entities.any { it.id == id } } ?: primaryEntity(found)?.id
        mutableState.update { it.copy(selectedDevice = device, selectedEntity = selected) }
    }
    /** Opening a saved placement selects its exact function and never sends a HA command. */
    fun selectBinding(id: String): Boolean {
        val current = state.value
        val binding = current.bindings.find { it.id == id && it.serverKey == current.serverKey } ?: return false
        val device = current.catalog.devices.find { it.key == binding.deviceKey } ?: return false
        if (device.entities.none { it.id == binding.entityId }) return false
        select(binding.deviceKey, binding.entityId)
        return true
    }
    fun showDemo() {
        stop(); val catalog = Demo.catalog(AppLanguage.language.value == "vi")
        mutableState.value = AppState(catalog, text(R.string.demo_status), true, true, Demo.KEY, selectedDevice = "device:lamp", selectedEntity = "light.living", bindings = allBindings.filter { it.serverKey == Demo.KEY })
    }
    fun disconnect(forget: Boolean = false) {
        val forgotten=if(forget) activeAuth ?: runCatching { credentials.loadAuth()?.let { ServerAddress.parse(it.first) to it.second } }.getOrNull() else null
        stop(); if (forget) credentials.clear(); mutableState.value = AppState(status = text(R.string.not_connected))
        forgotten?.takeIf { it.second.renewable }?.let { (address,auth) -> scope.launch { runCatching { authClient.revoke(address,auth) } } }
    }
    private fun stop(cancelLogin: Boolean = true) { if(cancelLogin) login.cancel(); generation++; connection?.cancel(); connection = null; session?.close(); session = null; activeAuth=null }
    fun connect(url: String, token: String, remember: Boolean) {
        val address = try { ServerAddress.parse(vn.homepanel.discovery.normalizeServerInput(url)) } catch (e: Exception) { notify(text(R.string.invalid_address)); return }
        if (token.isBlank()) { notify(text(R.string.enter_token)); return }
        connectCredentials(address,HaCredentials(token),remember)
    }
    private fun connectCredentials(address: ServerAddress, initial: HaCredentials, remember: Boolean, restoring: Boolean=false, cancelLogin: Boolean=true) {
        stop(cancelLogin); val run = generation
        mutableState.value = AppState(status = text(R.string.connecting), serverKey = address.key, serverUrl = address.base, bindings = allBindings.filter { it.serverKey == address.key })
        connection = scope.launch {
            var retry = 0; var stored = restoring; var auth=initial; var lastSaved:HaCredentials?=if(restoring) initial else null;var refreshedAfterRejection=false
            while (isActive && run == generation) {
                var live:HaSession?=null
                var collector: Job? = null
                try {
                    if(auth.needsRefresh()) auth=authClient.refresh(address,auth)
                    activeAuth=address to auth
                    val connectedSession=HaSession(address,auth.accessToken); live=connectedSession;session=connectedSession
                    connectedSession.connect()
                    if (!stored || lastSaved!=auth) { if (remember) credentials.saveAuth(address.base, auth) else credentials.clear(); stored = true;lastSaved=auth }
                    retry = 0
                    refreshedAfterRejection=false
                    mutableState.update { it.copy(connected = true, status = text(R.string.connected), message = null, catalog = connectedSession.catalog.value) }
                    collector = launch { connectedSession.catalog.collect { data -> mutableState.update { it.copy(catalog = data) } } }
                    if(auth.renewable) {
                        withTimeoutOrNull((auth.refreshAt-System.currentTimeMillis()).coerceAtLeast(1)) { connectedSession.ended.await() }
                        mutableState.update { it.copy(connected=false,status=text(R.string.auth_renewing),busy=emptySet()) }
                    } else connectedSession.ended.await()
                } catch (e: TimeoutCancellationException) {
                    if (run == generation) mutableState.update { it.copy(connected = false, status = text(R.string.ha_retrying), busy = emptySet(), message = text(R.string.check_network)) }
                } catch (e: CancellationException) { throw e }
                catch (e: AuthFailure) {
                    if(run!=generation) break
                    if(e.reason==AuthError.EXPIRED) { if(remember && stored) credentials.clear();activeAuth=null }
                    mutableState.update { it.copy(connected=false,busy=emptySet(),status=text(if(e.reason==AuthError.EXPIRED) R.string.auth_sign_in_again else R.string.auth_retrying),message=text(if(e.reason==AuthError.EXPIRED) R.string.auth_expired else R.string.auth_network_error)) }
                    if(e.reason==AuthError.EXPIRED) break
                }
                catch (e: Exception) {
                    if (run != generation) break
                    mutableState.update { it.copy(connected = false, status = text(R.string.disconnected_retry), busy = emptySet(), message = if (e is HaFailure) text(e.textId) else text(R.string.connection_failed)) }
                    if (e is HaFailure && e.authenticationRejected) {
                        if(auth.renewable && !refreshedAfterRejection) { auth=auth.copy(refreshAt=0);refreshedAfterRejection=true }
                        else { mutableState.update { it.copy(status = text(R.string.auth_sign_in_again)) };if(remember && stored) credentials.clear();activeAuth=null;break }
                    }
                } finally { collector?.cancel(); live?.close(); if (session === live) session = null }
                delay((1000L shl retry.coerceAtMost(5)).coerceAtMost(30_000L)); retry++
            }
        }
    }
    fun control(entityId: String, control: Control) {
        val current = state.value
        if (!current.connected || entityId in current.busy) return
        val entity = current.catalog.entities[entityId] ?: return
        val call = try { buildServiceCall(entity, control, current.catalog) } catch (e: Exception) { notify(text(R.string.unsupported_action)); return }
        val run = generation
        mutableState.update { it.copy(busy = it.busy + entityId, message = null) }
        scope.launch {
            try {
                if (current.demo) { mutableState.update { it.copy(catalog = Demo.apply(it.catalog, call), message = text(R.string.demo_updated)) } }
                else {
                    val live = session ?: throw HaFailure(R.string.not_connected)
                    live.command(call.toJson())
                    if (run == generation) notify(text(R.string.command_received))
                }
            } catch (e: TimeoutCancellationException) {
                if (run == generation) notify(text(R.string.command_uncertain))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (run == generation) notify(if (e is HaFailure) text(e.textId) else text(R.string.command_uncertain)) }
            finally { if (run == generation) mutableState.update { it.copy(busy = it.busy - entityId) } }
        }
    }
    fun saveBinding(binding: SpatialBinding): Boolean {
        if (!bindingsReadable) { notify(text(R.string.bindings_protected)); return false }
        if (binding.serverKey != state.value.serverKey) return false
        val replacement = allBindings.filterNot { it.id == binding.id || (it.serverKey == binding.serverKey && it.entityId == binding.entityId) } + binding
        return try { bindingStore.save(replacement); allBindings = replacement; refreshBindings(); notify(text(R.string.placement_saved, binding.label)); true }
        catch (_: Exception) { notify(text(R.string.placement_failed)); false }
    }
    fun deleteBinding(id: String) {
        if (!bindingsReadable) { notify(text(R.string.bindings_protected)); return }
        val replacement = allBindings.filterNot { it.id == id && it.serverKey == state.value.serverKey }
        try { bindingStore.save(replacement); allBindings = replacement; refreshBindings() } catch (_: Exception) { notify(text(R.string.placement_delete_failed)) }
    }
    private fun refreshBindings() { mutableState.update { it.copy(bindings = allBindings.filter { b -> b.serverKey == it.serverKey }) } }
}
