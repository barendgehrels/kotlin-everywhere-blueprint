package org.example.slippy.tile

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.slippy.foundation.ApiException
import org.example.slippy.foundation.ERROR_CODE_GUEST_QUOTA
import org.example.slippy.foundation.LocalStorage
import org.example.slippy.foundation.MemoryKeyValueStore
import org.example.slippy.foundation.MessageStore
import org.example.slippy.geocode.GeocodeGateway
import org.example.slippy.geocode.Place

private class FakeTileGateway : TileGateway {
    val requests = mutableListOf<Pair<Tile, CompletableDeferred<Result<ByteArray>>>>()

    /** When set, answers at once with this. */
    var result: Result<ByteArray>? = null

    override suspend fun tile(tile: Tile): Result<ByteArray> {
        result?.let { return it }
        val answer = CompletableDeferred<Result<ByteArray>>()
        requests += tile to answer
        return answer.await()
    }
}

private class FakeGeocodeGateway(var result: Result<Place>) : GeocodeGateway {
    override suspend fun geocode(placeName: String): Result<Place> = result
}

@OptIn(ExperimentalCoroutinesApi::class)
class TileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tiles = FakeTileGateway()
    private val geocode = FakeGeocodeGateway(Result.success(Place(LatLon(48.8566, 2.3522), "Paris, France")))
    private val messages = MessageStore()
    private val storage = LocalStorage(MemoryKeyValueStore())
    private val png = byteArrayOf(1, 2, 3)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TileViewModel(tiles, geocode, storage, messages)

    private fun TileViewModel.showLatLon(lat: String, lon: String, zoom: String) {
        onAction(TileAction.Edit(TileField.Lat, lat))
        onAction(TileAction.Edit(TileField.Lon, lon))
        onAction(TileAction.Edit(TileField.Zoom, zoom))
        onAction(TileAction.Show)
    }

    @Test
    fun pointShowsItsTileWithTheDot() = runTest(dispatcher) {
        tiles.result = Result.success(png)
        val viewModel = viewModel()
        viewModel.onAction(TileAction.SessionChanged(hasSession = true, canGeocode = false))
        viewModel.showLatLon("52.3702", "4.8952", "12")
        advanceUntilIdle()

        val state = viewModel.tileUiState.value
        assertEquals(Tile(12, 2103, 1346), state.shown?.tile)
        assertNotNull(state.shown?.dot)
        assertSame(png, state.image?.png)
        assertFalse(state.isLoading)
        assertEquals("2103", state.input.x)                      // the tile fields follow
        assertTrue(messages.messages.value.isEmpty())
    }

    @Test
    fun tileShowsItsBounds() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(TileAction.SelectMode(TileMode.Tile))
        viewModel.onAction(TileAction.Edit(TileField.Z, "1"))
        viewModel.onAction(TileAction.Edit(TileField.X, "0"))
        viewModel.onAction(TileAction.Edit(TileField.Y, "0"))
        viewModel.onAction(TileAction.Show)

        val shown = viewModel.tileUiState.value.shown
        assertEquals("85.051129", shown?.north)
        assertEquals("0.000000", shown?.south)
        assertEquals("-180.000000", shown?.west)
        assertEquals("0.000000", shown?.east)
        assertNull(shown?.dot)
    }

    @Test
    fun invalidInputShowsErrorsAtTheFields() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(TileAction.SessionChanged(hasSession = true, canGeocode = false))
        advanceUntilIdle()
        val before = tiles.requests.size
        viewModel.showLatLon("95", "4.9", "23")
        advanceUntilIdle()

        val errors = viewModel.tileUiState.value.errors
        assertNotNull(errors.lat)
        assertNull(errors.lon)
        assertNotNull(errors.zoom)
        assertEquals(before, tiles.requests.size)
    }

    @Test
    fun failureGoesToTheMessages() = runTest(dispatcher) {
        tiles.result = Result.failure(ApiException(ERROR_CODE_GUEST_QUOTA, "quota", 429))
        val viewModel = viewModel()
        viewModel.onAction(TileAction.SessionChanged(hasSession = true, canGeocode = false))
        viewModel.showLatLon("52.3702", "4.8952", "12")
        advanceUntilIdle()

        val state = viewModel.tileUiState.value
        assertNull(state.image)
        assertFalse(state.isLoading)
        assertEquals(1, messages.messages.value.size)
        assertTrue(messages.messages.value.single().isError)
    }

    @Test
    fun overtakenTileRequestIsDropped() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(TileAction.SessionChanged(hasSession = true, canGeocode = false))
        advanceUntilIdle()
        viewModel.showLatLon("52.3702", "4.8952", "5")
        advanceUntilIdle()
        viewModel.showLatLon("52.3702", "4.8952", "6")
        advanceUntilIdle()

        val (older, olderAnswer) = tiles.requests[tiles.requests.size - 2]
        val (newer, newerAnswer) = tiles.requests.last()
        val newerPng = byteArrayOf(6)
        newerAnswer.complete(Result.success(newerPng))
        olderAnswer.complete(Result.success(byteArrayOf(5)))    // arrives late
        advanceUntilIdle()

        val state = viewModel.tileUiState.value
        assertEquals(5, older.z)
        assertEquals(newer, state.shown?.tile)
        assertSame(newerPng, state.image?.png)
    }

    @Test
    fun withoutSessionTheMathWorksButNoTileIsLoaded() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.showLatLon("52.3702", "4.8952", "12")
        advanceUntilIdle()

        val state = viewModel.tileUiState.value
        assertEquals(Tile(12, 2103, 1346), state.shown?.tile)
        assertFalse(state.hasSession)
        assertTrue(tiles.requests.isEmpty())
    }

    @Test
    fun foundPlaceFillsThePointAndKeepsTheZoom() = runTest(dispatcher) {
        tiles.result = Result.success(png)
        val viewModel = viewModel()
        viewModel.onAction(TileAction.SessionChanged(hasSession = true, canGeocode = true))
        advanceUntilIdle()
        viewModel.onAction(TileAction.Edit(TileField.Zoom, "10"))
        viewModel.onAction(TileAction.SelectMode(TileMode.Place))
        viewModel.onAction(TileAction.Edit(TileField.Place, "Paris"))
        viewModel.onAction(TileAction.FindPlace)
        advanceUntilIdle()

        val state = viewModel.tileUiState.value
        assertEquals(TileMode.LatLon, state.input.mode)
        assertEquals("48.8566", state.input.lat)
        assertEquals("2.3522", state.input.lon)
        assertEquals("10", state.input.zoom)
        assertEquals("Paris, France", state.foundName)
        assertEquals(latLonToTile(LatLon(48.8566, 2.3522), 10), state.shown?.tile)
    }

    // Answers that differ below what the screen shows give an equal state: no new frame
    @Test
    fun answersThatLookTheSameGiveAnEqualState() = runTest(dispatcher) {
        tiles.result = Result.success(png)

        suspend fun stateAfterFinding(lat: Double, lon: Double): TileUiState {
            geocode.result = Result.success(Place(LatLon(lat, lon), "Paris, France"))
            val viewModel = TileViewModel(tiles, geocode, LocalStorage(MemoryKeyValueStore()), messages)
            viewModel.onAction(TileAction.SessionChanged(hasSession = true, canGeocode = true))
            advanceUntilIdle()
            viewModel.onAction(TileAction.Edit(TileField.Place, "Paris"))
            viewModel.onAction(TileAction.FindPlace)
            advanceUntilIdle()
            // The image is a new download each time; the rest must be equal
            return viewModel.tileUiState.value.copy(image = null)
        }

        assertEquals(stateAfterFinding(48.85660001, 2.35220002), stateAfterFinding(48.85659999, 2.35219998))
    }

    @Test
    fun inputIsRestoredAtStart() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.onAction(TileAction.SelectMode(TileMode.Tile))
        first.onAction(TileAction.Edit(TileField.Z, "3"))
        advanceUntilIdle()

        val second = viewModel()
        advanceUntilIdle()
        assertEquals(TileMode.Tile, second.tileUiState.value.input.mode)
        assertEquals("3", second.tileUiState.value.input.z)
    }
}
