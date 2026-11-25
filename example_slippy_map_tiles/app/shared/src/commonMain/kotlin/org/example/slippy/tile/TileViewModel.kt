package org.example.slippy.tile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.example.slippy.foundation.LocalStorage
import org.example.slippy.foundation.MessageStore
import org.example.slippy.foundation.NotLoggedInException
import org.example.slippy.foundation.StorageEntries
import org.example.slippy.geocode.GeocodeGateway
import org.example.slippy.geocode.PLACE_NAME_MAX_LENGTH
import org.example.slippy.geocode.isPlaceNameValid
import org.example.slippy.resources.Res
import org.example.slippy.resources.error_latitude
import org.example.slippy.resources.error_longitude
import org.example.slippy.resources.error_place_name
import org.example.slippy.resources.error_tile_coordinate
import org.example.slippy.resources.error_whole_number
import org.example.slippy.resources.error_zoom
import org.jetbrains.compose.resources.StringResource

/** What the input is: a point and a zoom level, a tile, or a place name (AI). */
enum class TileMode { LatLon, Tile, Place }

enum class TileField { Lat, Lon, Zoom, Z, X, Y, Place }

/** All fields, as typed, and the mode: remembered in local storage, as one entry. */
@Serializable
data class TileInput(
    val mode: TileMode = TileMode.LatLon,
    val lat: String = "52.373053",
    val lon: String = "4.892759",
    val zoom: String = "12",
    val z: String = "12",
    val x: String = "2103",
    val y: String = "1346",
    val place: String = "",
)

data class FieldError(val resId: StringResource, val args: List<String> = emptyList())

data class TileFieldErrors(
    val lat: FieldError? = null,
    val lon: FieldError? = null,
    val zoom: FieldError? = null,
    val z: FieldError? = null,
    val x: FieldError? = null,
    val y: FieldError? = null,
    val place: FieldError? = null,
) {
    val isEmpty: Boolean get() = this == TileFieldErrors()
}

/** The point's place in the tile, 0..1 from the west and the north edge, rounded as drawn. */
data class Dot(val x: Double, val y: Double)

/** The tile that is shown, with its bounds as the screen shows them. */
data class ShownTile(
    val tile: Tile,
    val dot: Dot?,
    val north: String,
    val south: String,
    val west: String,
    val east: String,
)

/** A tile's PNG. Equal only to itself: a new download is a new image. */
class TileImage(val tile: Tile, val png: ByteArray)

data class TileUiState(
    val input: TileInput = TileInput(),
    val errors: TileFieldErrors = TileFieldErrors(),
    val shown: ShownTile? = null,
    val image: TileImage? = null,
    val isLoading: Boolean = false,
    val foundName: String? = null,
    val isGeocoding: Boolean = false,
    val hasSession: Boolean = false,       // tiles need a session, a guest's too
    val canGeocode: Boolean = false,       // a registered user with a verified email
)

sealed interface TileAction {
    data class SelectMode(val mode: TileMode) : TileAction
    data class Edit(val field: TileField, val text: String) : TileAction
    data object Show : TileAction
    data object FindPlace : TileAction
    data class SessionChanged(val hasSession: Boolean, val canGeocode: Boolean) : TileAction
}

class TileViewModel(
    private val tiles: TileGateway,
    private val geocode: GeocodeGateway,
    private val storage: LocalStorage,
    private val messages: MessageStore,
) : ViewModel() {
    val tileUiState: StateFlow<TileUiState>
        field = MutableStateFlow(TileUiState())

    private var tileJob: Job? = null
    private var loadingTile: Tile? = null
    private var geocodeJob: Job? = null
    private var saveJob: Job? = null
    private var isTouched = false        // the user was quicker than the restore

    init {
        viewModelScope.launch {
            val stored = storage.get(StorageEntries.TILE_INPUT)
            if (isTouched) return@launch
            val input = stored ?: TileInput()
            val state = tileUiState.value.copy(input = input)
            showAndLoad(withShownTile(state, showErrors = false))
        }
    }

    fun onAction(action: TileAction) = when (action) {
        is TileAction.SelectMode -> selectMode(action.mode)
        is TileAction.Edit -> edit(action.field, action.text)
        TileAction.Show -> showAndLoad(withShownTile(tileUiState.value, showErrors = true))
        TileAction.FindPlace -> findPlace()
        is TileAction.SessionChanged -> sessionChanged(action.hasSession, action.canGeocode)
    }

    private fun selectMode(mode: TileMode) {
        val state = tileUiState.value
        if (mode == TileMode.Place && !state.canGeocode) return
        val input = state.input.copy(mode = mode)
        tileUiState.value = state.copy(input = input)
        save(input)
    }

    private fun edit(field: TileField, text: String) {
        val state = tileUiState.value
        val (input, errors) = with(state) {
            when (field) {
                TileField.Lat -> input.copy(lat = text) to errors.copy(lat = null)
                TileField.Lon -> input.copy(lon = text) to errors.copy(lon = null)
                TileField.Zoom -> input.copy(zoom = text) to errors.copy(zoom = null)
                TileField.Z -> input.copy(z = text) to errors.copy(z = null)
                TileField.X -> input.copy(x = text) to errors.copy(x = null)
                TileField.Y -> input.copy(y = text) to errors.copy(y = null)
                TileField.Place -> input.copy(place = text) to errors.copy(place = null)
            }
        }
        // A changed point is no longer the place that was found
        val foundName = if (field == TileField.Lat || field == TileField.Lon) null else state.foundName
        tileUiState.value = state.copy(input = input, errors = errors, foundName = foundName)
        save(input)
    }

    private fun findPlace() {
        val state = tileUiState.value
        val placeName = state.input.place.trim()
        if (!isPlaceNameValid(placeName)) {
            val error = FieldError(Res.string.error_place_name, listOf(PLACE_NAME_MAX_LENGTH.toString()))
            tileUiState.value = state.copy(errors = state.errors.copy(place = error))
            return
        }
        // A newer search overtakes an older one
        geocodeJob?.cancel()
        tileUiState.value = state.copy(isGeocoding = true, errors = state.errors.copy(place = null))
        geocodeJob = viewModelScope.launch {
            geocode.geocode(placeName).fold(
                onSuccess = { place ->
                    // The zoom level stays as it is
                    val current = tileUiState.value
                    val input = current.input.copy(
                        mode = TileMode.LatLon,
                        lat = formatCoordinate(place.latLon.lat),
                        lon = formatCoordinate(place.latLon.lon),
                    )
                    val found = current.copy(input = input, foundName = place.name, isGeocoding = false)
                    showAndLoad(withShownTile(found, showErrors = true))
                    save(input)
                },
                onFailure = { error ->
                    tileUiState.update { it.copy(isGeocoding = false) }
                    messages.show(error)
                },
            )
        }
    }

    private fun sessionChanged(hasSession: Boolean, canGeocode: Boolean) {
        val state = tileUiState.value
        val input = if (!canGeocode && state.input.mode == TileMode.Place) state.input.copy(mode = TileMode.LatLon) else state.input
        showAndLoad(state.copy(hasSession = hasSession, canGeocode = canGeocode, input = input))
    }

    /**
     * Sets the state, once, and loads the shown tile when it is not there yet. A newer tile
     * request cancels the older one, so a slow answer never replaces a newer tile.
     */
    private fun showAndLoad(next: TileUiState) {
        val tile = next.shown?.tile
        val needsLoad = tile != null && next.hasSession && next.image?.tile != tile
        val onItsWay = needsLoad && loadingTile == tile
        if (!onItsWay) {
            tileJob?.cancel()
            tileJob = null
            loadingTile = null
        }
        tileUiState.value = next.copy(
            isLoading = needsLoad,
            image = next.image?.takeIf { next.hasSession && it.tile == tile },
        )
        if (!needsLoad || onItsWay) return
        loadingTile = tile
        tileJob = viewModelScope.launch {
            val result = tiles.tile(tile)
            loadingTile = null
            result.fold(
                onSuccess = { png -> tileUiState.update { it.copy(image = TileImage(tile, png), isLoading = false) } },
                onFailure = { error ->
                    tileUiState.update { it.copy(isLoading = false) }
                    // Without a session, the tile area says so itself
                    if (error !is NotLoggedInException) messages.show(error)
                },
            )
        }
    }

    private fun save(input: TileInput) {
        isTouched = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch { storage.set(StorageEntries.TILE_INPUT, input) }
    }
}

/**
 * The state with the tile of its input, worked out with core's math, or with the errors of the
 * fields (only shown on request). A point fills in the tile fields too.
 */
internal fun withShownTile(state: TileUiState, showErrors: Boolean): TileUiState {
    val input = state.input
    return when (input.mode) {
        TileMode.LatLon, TileMode.Place -> {
            val lat = parseDecimal(input.lat)?.takeIf(::isLatitudeValid)
            val lon = parseDecimal(input.lon)?.takeIf(::isLongitudeValid)
            val zoom = parseWholeNumber(input.zoom)?.takeIf(::isZoomValid)
            if (lat == null || lon == null || zoom == null) {
                if (!showErrors) return state
                val errors = state.errors.copy(
                    lat = if (lat == null) FieldError(Res.string.error_latitude) else null,
                    lon = if (lon == null) FieldError(Res.string.error_longitude) else null,
                    zoom = if (zoom == null) zoomError() else null,
                )
                return state.copy(errors = errors)
            }
            val position = latLonToTilePosition(LatLon(lat, lon), zoom)
            val tile = position.tile
            val dot = Dot(roundTo(position.fractionX, FRACTION_DECIMALS), roundTo(position.fractionY, FRACTION_DECIMALS))
            state.copy(
                input = input.copy(z = tile.z.toString(), x = tile.x.toString(), y = tile.y.toString()),
                errors = state.errors.copy(lat = null, lon = null, zoom = null),
                shown = shownTile(tile, dot),
            )
        }
        TileMode.Tile -> {
            val z = parseWholeNumber(input.z)?.takeIf(::isZoomValid)
            val x = parseWholeNumber(input.x)
            val y = parseWholeNumber(input.y)
            val tile = if (z != null && x != null && y != null) Tile(z, x, y) else null
            if (tile == null || !isTileValid(tile)) {
                if (!showErrors) return state
                val errors = state.errors.copy(
                    z = if (z == null) zoomError() else null,
                    x = coordinateError(x, z),
                    y = coordinateError(y, z),
                )
                return state.copy(errors = errors)
            }
            state.copy(errors = state.errors.copy(z = null, x = null, y = null), shown = shownTile(tile, dot = null))
        }
    }
}

private fun zoomError() = FieldError(Res.string.error_zoom, listOf(MIN_ZOOM.toString(), MAX_ZOOM.toString()))

private fun coordinateError(value: Int?, z: Int?): FieldError? = when {
    value == null || value < 0 -> FieldError(Res.string.error_whole_number)
    z != null && value >= tileCount(z) -> FieldError(Res.string.error_tile_coordinate, listOf((tileCount(z) - 1).toString()))
    else -> null
}

private fun shownTile(tile: Tile, dot: Dot?): ShownTile {
    val bounds = tileBounds(tile)
    return ShownTile(
        tile = tile,
        dot = dot,
        north = formatDecimal(bounds.north, COORDINATE_DECIMALS),
        south = formatDecimal(bounds.south, COORDINATE_DECIMALS),
        west = formatDecimal(bounds.west, COORDINATE_DECIMALS),
        east = formatDecimal(bounds.east, COORDINATE_DECIMALS),
    )
}
