package net.neuralcheck.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCCard
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.StatusFail
import net.neuralcheck.presentation.theme.StatusPass
import net.neuralcheck.presentation.theme.Surface
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerSurveyScreen(
    navController: NavController,
    scanId: String,
    viewModel: net.neuralcheck.presentation.viewmodel.PeerSurveyViewModel = hiltViewModel()
) {
    var q1 by remember { mutableStateOf<Boolean?>(null) }
    var q2 by remember { mutableStateOf<Boolean?>(null) }
    var q3 by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        containerColor = DeepSpace,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_peer_survey), color = TextPrimary) },
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
        ) {
            Text(
                text = stringResource(R.string.survey_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(20.dp))

            SurveyQuestion(stringResource(R.string.survey_battery_drains), q1) { q1 = it }
            Spacer(Modifier.height(12.dp))
            SurveyQuestion(stringResource(R.string.survey_overheats), q2) { q2 = it }
            Spacer(Modifier.height(12.dp))
            SurveyQuestion(stringResource(R.string.survey_sluggish), q3) { q3 = it }

            Spacer(Modifier.weight(1f))
            NCPrimaryButton(
                text = stringResource(R.string.peer_submit),
                enabled = q1 != null && q2 != null && q3 != null,
                onClick = {
                    viewModel.batteryDrainsFast.value = q1
                    viewModel.overheats.value = q2
                    viewModel.sluggish.value = q3
                    viewModel.save(scanId) { navController.popBackStack() }
                }
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_skip), color = TextSecondary) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SurveyQuestion(question: String, answer: Boolean?, onAnswer: (Boolean) -> Unit) {
    NCCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SurveyAnswerButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.survey_yes),
                active = answer == true,
                activeColor = StatusFail,
                onClick = { onAnswer(true) }
            )
            SurveyAnswerButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.survey_no),
                active = answer == false,
                activeColor = StatusPass,
                onClick = { onAnswer(false) }
            )
        }
    }
}

@Composable
private fun SurveyAnswerButton(
    modifier: Modifier,
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) activeColor.copy(alpha = 0.2f) else Surface,
            contentColor = if (active) activeColor else TextSecondary
        )
    ) { Text(label) }
}
