@file:OptIn(ExperimentalMaterial3Api::class)
package net.neuralcheck.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.CardDark
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.ShopSetupViewModel

/**
 * Shop owner storefront editor. Reachable from ProfileScreen for users on
 * `shop_basic` / `shop_pro` plans only — the entry point is gated upstream
 * so this screen does no plan-check itself, on the assumption a user who
 * navigated here has a valid subscription.
 *
 * Layout (top-to-bottom):
 *  - Logo circle (tap to pick a new image; preview decoded off-thread)
 *  - Shop name (required, max 80 chars — enforced in the VM)
 *  - Description (optional, max 200 chars)
 *  - City (optional)
 *  - Contact phone (optional, KeyboardType.Phone)
 *  - Street address (optional)
 *  - Save button (disabled while uploading)
 *
 * Save handling lives in the VM. The screen only flips the inline name
 * error and surfaces network errors in a small status line above the
 * Save button.
 */
@Composable
fun ShopSetupScreen(
    navController: NavController,
    viewModel: ShopSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // System image picker. `GetContent("image/*")` is broadly supported back
    // to the API levels we ship to and returns a Uri the Storage SDK can
    // stream directly via `putFile`. The newer `PickVisualMedia` would also
    // work but adds a dependency on PhotoPicker availability — `GetContent`
    // gracefully falls back to the documents UI on older devices.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.setLogoUri(uri) }

    // Decode the picked image off the main thread. Falls back to null on any
    // decode error so the placeholder icon takes over instead of crashing.
    val previewBitmap by produceState<Bitmap?>(initialValue = null, state.pickedLogoUri) {
        val uri = state.pickedLogoUri
        if (uri == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
    }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shop_setup_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoPicker(
                previewBitmap = previewBitmap,
                savedLogoUrl = state.savedLogoUrl,
                onPick = { pickImage.launch("image/*") }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (previewBitmap == null && state.savedLogoUrl.isBlank()) R.string.shop_setup_pick_logo
                    else R.string.shop_setup_change_logo
                ),
                color = NeuralGreen,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { pickImage.launch("image/*") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(20.dp))

            ShopField(
                value = state.shopName,
                onValueChange = viewModel::setShopName,
                label = stringResource(R.string.shop_setup_name_label),
                isError = state.nameError,
                supportingText = if (state.nameError) stringResource(R.string.shop_setup_name_required) else null
            )

            Spacer(Modifier.height(12.dp))

            // ── Slug field ──────────────────────────────────────────────
            // Lets the user pick their own URL fragment. Optional — empty
            // input keeps the existing slug (or auto-generates one on first
            // save). The supportingText shows a live preview of the public
            // URL so the user understands what they're choosing.
            //
            // The supportingText cycles between three messages:
            //   1. INVALID_FORMAT  → "حروف إنجليزية وأرقام وشرطات فقط (3-30)"
            //   2. ALREADY_TAKEN   → "هذا الرابط مأخوذ، اختر آخر"
            //   3. otherwise       → URL preview with the current slug
            //                        (or the helper hint when the field is empty)
            val slugSupportingText = when (state.slugError) {
                ShopSetupViewModel.SlugError.INVALID_FORMAT ->
                    stringResource(R.string.shop_setup_slug_invalid)
                ShopSetupViewModel.SlugError.ALREADY_TAKEN ->
                    stringResource(R.string.shop_setup_slug_taken)
                null -> if (state.shopSlug.isBlank()) {
                    stringResource(R.string.shop_setup_slug_helper)
                } else {
                    stringResource(R.string.shop_setup_slug_url_preview_fmt, state.shopSlug)
                }
            }
            ShopField(
                value = state.shopSlug,
                onValueChange = viewModel::setShopSlug,
                label = stringResource(R.string.shop_setup_slug_label),
                isError = state.slugError != null,
                supportingText = slugSupportingText,
                placeholder = stringResource(R.string.shop_setup_slug_placeholder),
                // ASCII-only keyboard so the OS hint pushes users toward
                // the format that will pass validation. The VM still
                // sanitizes anyway, so this is hint-only.
                keyboardType = KeyboardType.Ascii
            )

            Spacer(Modifier.height(12.dp))

            ShopField(
                value = state.shopDescription,
                onValueChange = viewModel::setShopDescription,
                label = stringResource(R.string.shop_setup_description_label),
                supportingText = stringResource(R.string.shop_setup_description_hint),
                singleLine = false,
                minLines = 3
            )

            Spacer(Modifier.height(12.dp))

            ShopField(
                value = state.shopCity,
                onValueChange = viewModel::setShopCity,
                label = stringResource(R.string.shop_setup_city_label),
                supportingText = stringResource(R.string.shop_setup_city_hint)
            )

            Spacer(Modifier.height(12.dp))

            ShopField(
                value = state.shopPhone,
                onValueChange = viewModel::setShopPhone,
                label = stringResource(R.string.shop_setup_phone_label),
                keyboardType = KeyboardType.Phone
            )

            Spacer(Modifier.height(12.dp))

            ShopField(
                value = state.shopAddress,
                onValueChange = viewModel::setShopAddress,
                label = stringResource(R.string.shop_setup_address_label),
                singleLine = false,
                minLines = 2
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusFail
                )
            }

            Spacer(Modifier.height(24.dp))

            NCPrimaryButton(
                text = stringResource(R.string.shop_setup_save),
                enabled = !state.isSaving && !state.isLoading,
                onClick = { viewModel.save() }
            )

            // Centred spinner + caption while a save is in flight. Stays
            // beneath the button so the user keeps the form context visible.
            if (state.isSaving) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = NeuralGreen,
                    strokeWidth = 2.dp
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Tappable circular logo picker. Stacks (in order of priority):
 *  1. The just-picked Uri preview (decoded off-thread).
 *  2. A saved-but-unchanged storefront logo URL is *not* loaded by this
 *     screen — we don't pull in Coil/Glide just for one image. Instead we
 *     show a "logo on file" placeholder so the user knows there's already
 *     one stored without us having to fetch it.
 *  3. A neutral storefront placeholder when neither is available.
 */
@Composable
private fun LogoPicker(
    previewBitmap: Bitmap?,
    savedLogoUrl: String,
    onPick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Surface)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        when {
            previewBitmap != null -> Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
            savedLogoUrl.isNotBlank() -> {
                // Existing remote logo — show a "saved" indicator. Loading
                // the actual image would need an HTTP image library; we
                // intentionally avoid that dependency for a single screen.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Storefront,
                        null,
                        tint = NeuralGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(NeuralGreen.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeuralGreen
                        )
                    }
                }
            }
            else -> Icon(
                Icons.Default.AddPhotoAlternate,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/**
 * Outlined text field tuned for the shop form.
 *
 * Earlier this helper forced a fixed `.height(56.dp)` on every field. When
 * a `supportingText` was attached (city hint, description hint, name
 * error) the supporting row competed with the input + floating label for
 * space inside the rigid 56dp box, and the typed characters disappeared
 * behind the label. The fix is to *not* constrain the height — Material3
 * sizes the field correctly when left alone — and to use `minLines` for
 * the multi-line variants instead of synthetic dp padding.
 *
 * Text colours are pinned to pure white so we never re-introduce the same
 * bug via theme drift; cursor + focused border stay branded NeuralGreen.
 */
@Composable
private fun ShopField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = TextMuted) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeuralGreen,
            unfocusedBorderColor = CardBorder,
            errorBorderColor = StatusFail,
            focusedLabelColor = NeuralGreen,
            unfocusedLabelColor = TextSecondary,
            cursorColor = NeuralGreen,
            focusedTextColor = androidx.compose.ui.graphics.Color.White,
            unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
            disabledTextColor = androidx.compose.ui.graphics.Color.White,
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark,
            disabledContainerColor = CardDark
        )
    )
}
