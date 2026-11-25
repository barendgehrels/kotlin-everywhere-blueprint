package org.example.slippy.tile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.example.slippy.foundation.SlippyTheme
import org.example.slippy.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
fun TileContent(uiState: TileUiState, isPortrait: Boolean, onAction: (TileAction) -> Unit) {
    // A short window scrolls: a phone browser in landscape leaves little height
    val scroll = rememberScrollState()
    if (isPortrait) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Inputs(uiState, onAction, Modifier.widthIn(max = 480.dp).fillMaxWidth())
            Result(uiState)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Inputs(uiState, onAction, Modifier.weight(1f).widthIn(max = 480.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Result(uiState)
            }
        }
    }
}

@Composable
private fun Inputs(uiState: TileUiState, onAction: (TileAction) -> Unit, modifier: Modifier) {
    val input = uiState.input
    val errors = uiState.errors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeSelector(input.mode, uiState.canGeocode, onAction)
        when (input.mode) {
            TileMode.LatLon -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(input.lat, Res.string.latitude, errors.lat, decimal = true, Modifier.weight(1f)) {
                        onAction(TileAction.Edit(TileField.Lat, it))
                    }
                    NumberField(input.lon, Res.string.longitude, errors.lon, decimal = true, Modifier.weight(1f)) {
                        onAction(TileAction.Edit(TileField.Lon, it))
                    }
                }
                NumberField(input.zoom, Res.string.zoom, errors.zoom, decimal = false, Modifier.fillMaxWidth()) {
                    onAction(TileAction.Edit(TileField.Zoom, it))
                }
                uiState.foundName?.let { Text(stringResource(Res.string.found, it)) }
                Button(onClick = { onAction(TileAction.Show) }) { Text(stringResource(Res.string.show_tile)) }
            }
            TileMode.Tile -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(input.z, Res.string.tile_z, errors.z, decimal = false, Modifier.weight(1f)) {
                        onAction(TileAction.Edit(TileField.Z, it))
                    }
                    NumberField(input.x, Res.string.tile_x, errors.x, decimal = false, Modifier.weight(1f)) {
                        onAction(TileAction.Edit(TileField.X, it))
                    }
                    NumberField(input.y, Res.string.tile_y, errors.y, decimal = false, Modifier.weight(1f)) {
                        onAction(TileAction.Edit(TileField.Y, it))
                    }
                }
                Button(onClick = { onAction(TileAction.Show) }) { Text(stringResource(Res.string.show_tile)) }
            }
            TileMode.Place -> {
                OutlinedTextField(
                    value = input.place,
                    onValueChange = { onAction(TileAction.Edit(TileField.Place, it)) },
                    label = { Text(stringResource(Res.string.place_name)) },
                    isError = errors.place != null,
                    supportingText = {
                        val error = errors.place
                        if (error != null) ErrorText(error) else Text(stringResource(Res.string.place_explanation))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onAction(TileAction.FindPlace) }, enabled = !uiState.isGeocoding) {
                        Text(stringResource(Res.string.find_place))
                    }
                    if (uiState.isGeocoding) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: TileMode, canGeocode: Boolean, onAction: (TileAction) -> Unit) {
    // The place name only for a registered, verified user: hidden otherwise, not shown dead
    val modes = if (canGeocode) TileMode.entries else listOf(TileMode.LatLon, TileMode.Tile)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, each ->
            SegmentedButton(
                selected = each == mode,
                onClick = { onAction(TileAction.SelectMode(each)) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) {
                Text(stringResource(each.label))
            }
        }
    }
}

private val TileMode.label: StringResource
    get() = when (this) {
        TileMode.LatLon -> Res.string.mode_lat_lon
        TileMode.Tile -> Res.string.mode_tile
        TileMode.Place -> Res.string.mode_place
    }

@Composable
private fun NumberField(
    value: String,
    label: StringResource,
    error: FieldError?,
    decimal: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { ErrorText(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ErrorText(error: FieldError) {
    Text(stringResource(error.resId, *error.args.toTypedArray()))
}

@Composable
private fun Result(uiState: TileUiState) {
    val shown = uiState.shown
    if (shown != null) {
        val tile = shown.tile
        Text(stringResource(Res.string.tile_zxy, tile.z, tile.x, tile.y), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.bounds, shown.north, shown.south, shown.west, shown.east),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
    TileArea(uiState)
    Text(
        stringResource(Res.string.attribution),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TileArea(uiState: TileUiState) {
    val shown = uiState.shown
    val image = uiState.image?.takeIf { it.tile == shown?.tile }
    // Compose decodes the PNG itself: no image library
    val bitmap: ImageBitmap? = remember(image) { image?.let { runCatching { it.png.decodeToImageBitmap() }.getOrNull() } }
    Box(
        modifier = Modifier.size(256.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !uiState.hasSession -> Message(Res.string.tile_needs_session)
            bitmap != null -> {
                Image(bitmap, contentDescription = stringResource(Res.string.tile_description), Modifier.fillMaxSize())
                shown?.dot?.let { Dot(it) }
            }
            uiState.isLoading -> CircularProgressIndicator()
            image != null -> Message(Res.string.tile_unreadable)
            else -> Message(Res.string.tile_none)
        }
    }
}

@Composable
private fun Message(text: StringResource) {
    Text(
        stringResource(text),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun Dot(dot: Dot) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset((size.width * dot.x).toFloat(), (size.height * dot.y).toFloat())
        drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
        drawCircle(Color.Red, radius = 5.dp.toPx(), center = center)
    }
}

@Preview
@Composable
fun TilePreview() = SlippyTheme {
    TileContent(
        uiState = TileUiState(
            input = TileInput(lat = "52.373053", lon = "4.892759", zoom = "12"),
            errors = TileFieldErrors(zoom = FieldError(Res.string.error_zoom, listOf("0", "22"))),
            shown = ShownTile(
                tile = Tile(12, 2103, 1346),
                dot = Dot(0.71, 0.45),
                north = "52.375599", south = "52.321911", west = "4.833984", east = "4.921875",
            ),
            foundName = "Amsterdam, Netherlands",
            hasSession = true,
            canGeocode = true,
        ),
        isPortrait = true,
        onAction = {},
    )
}
