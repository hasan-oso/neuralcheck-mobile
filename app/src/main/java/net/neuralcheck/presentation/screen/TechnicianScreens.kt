@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.CardBorder
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.OutfitFamily
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary
import net.neuralcheck.presentation.viewmodel.TechnicianViewModel

@Composable
fun TechPreRepairScreen(
    navController: NavController,
    scanId: String,
    viewModel: TechnicianViewModel = hiltViewModel()
) {
    val complaint by viewModel.complaint.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val categories = listOf(
        stringResource(R.string.tech_cat_battery),
        stringResource(R.string.tech_cat_screen),
        stringResource(R.string.tech_cat_cpu),
        stringResource(R.string.tech_cat_camera),
        stringResource(R.string.tech_cat_audio),
        stringResource(R.string.tech_cat_charging),
        stringResource(R.string.tech_cat_other)
    )

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_tech_pre_repair), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace, titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            NCTextField(
                value = complaint,
                onValueChange = { viewModel.complaint.value = it },
                label = stringResource(R.string.tech_complaint_label),
                minLines = 2
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.tech_fault_categories),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    BrandChip(
                        selected = cat in selectedCategories,
                        label = cat,
                        onClick = {
                            viewModel.selectedCategories.value =
                                if (cat in selectedCategories) selectedCategories - cat
                                else selectedCategories + cat
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.tech_confidence),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${confidence.toInt()}/5",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = NeuralGreen
                )
            }
            Slider(
                value = confidence,
                onValueChange = { viewModel.confidence.value = it },
                valueRange = 1f..5f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = NeuralGreen,
                    activeTrackColor = NeuralGreen,
                    inactiveTrackColor = Surface,
                    activeTickColor = DeepSpace,
                    inactiveTickColor = TextMuted
                )
            )
            Spacer(Modifier.height(16.dp))
            NCTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = stringResource(R.string.tech_notes),
                minLines = 2
            )
            Spacer(Modifier.height(24.dp))
            NCPrimaryButton(
                text = stringResource(R.string.tech_submit),
                onClick = { viewModel.savePreRepair(scanId) { navController.popBackStack() } }
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun TechPostRepairScreen(
    navController: NavController,
    scanId: String,
    viewModel: TechnicianViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val categories = listOf(
        stringResource(R.string.tech_cat_battery),
        stringResource(R.string.tech_cat_screen),
        stringResource(R.string.tech_cat_cpu),
        stringResource(R.string.tech_cat_camera),
        stringResource(R.string.tech_cat_audio),
        stringResource(R.string.tech_cat_charging),
        stringResource(R.string.tech_cat_other)
    )
    val repairOptions = listOf(
        stringResource(R.string.tech_repair_full),
        stringResource(R.string.tech_repair_partial),
        stringResource(R.string.tech_repair_none)
    )
    var repairResult by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_tech_post_repair), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpace, titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.tech_what_repaired),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    BrandChip(
                        selected = cat in selectedCategories,
                        label = cat,
                        onClick = {
                            viewModel.selectedCategories.value =
                                if (cat in selectedCategories) selectedCategories - cat
                                else selectedCategories + cat
                        }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.tech_repair_result),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            repairOptions.forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = repairResult == i,
                        onClick = { repairResult = i },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = NeuralGreen,
                            unselectedColor = TextMuted
                        )
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            NCTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = stringResource(R.string.tech_notes),
                minLines = 2
            )
            Spacer(Modifier.height(24.dp))
            // ملحق القرار ١٠٤ البند ٤: was tech_save_and_compare ("حفظ
            // وعرض المقارنة" / "Kaydet ve karşılaştırmayı göster") — this
            // button only ever calls savePostRepair then popBackStack(),
            // never showing any comparison, since its first commit. Now
            // btn_save ("حفظ" / "Kaydet", already committed with a Turkish
            // translation — values/strings.xml:823, values-tr/strings.xml:597)
            // — text only, behaviour unchanged.
            NCPrimaryButton(
                text = stringResource(R.string.btn_save),
                onClick = {
                    viewModel.savePostRepair(scanId, repairOptions[repairResult], selectedCategories) {
                        navController.popBackStack()
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NCTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedBorderColor = NeuralGreen,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = NeuralGreen,
            unfocusedLabelColor = TextMuted,
            cursorColor = NeuralGreen
        )
    )
}

@Composable
private fun BrandChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface,
            labelColor = TextSecondary,
            selectedContainerColor = NeuralGreen.copy(alpha = 0.15f),
            selectedLabelColor = NeuralGreen
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CardBorder,
            selectedBorderColor = NeuralGreen
        )
    )
}
