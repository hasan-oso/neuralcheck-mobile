package net.neuralcheck.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.neuralcheck.R
import net.neuralcheck.presentation.components.NCPrimaryButton
import net.neuralcheck.presentation.theme.DeepSpace
import net.neuralcheck.presentation.theme.NeonCyan
import net.neuralcheck.presentation.theme.NeuralGreen
import net.neuralcheck.presentation.theme.SkyCyan
import net.neuralcheck.presentation.theme.TextMuted
import net.neuralcheck.presentation.theme.TextPrimary
import net.neuralcheck.presentation.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    // Each page gets its own accent colour drawn from the brand palette so
    // the carousel has rhythm without straying from the identity.
    val pages = listOf(
        OnboardingPage(Icons.Default.PhoneAndroid, NeuralGreen, R.string.onboarding_title_1, R.string.onboarding_desc_1),
        OnboardingPage(Icons.Default.Description, SkyCyan, R.string.onboarding_title_2, R.string.onboarding_desc_2),
        OnboardingPage(Icons.Default.Build, NeonCyan, R.string.onboarding_title_3, R.string.onboarding_desc_3)
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(onClick = onComplete) {
                    Text(
                        text = stringResource(R.string.btn_skip),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(pages[page].tint.copy(alpha = 0.12f), RoundedCornerShape(42.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = pages[page].icon,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = pages[page].tint
                    )
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    text = stringResource(pages[page].titleRes),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(pages[page].descRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Dots indicator — active dot uses the page accent colour.
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .background(
                            color = if (active) pages[i].tint else TextMuted.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)) {
            NCPrimaryButton(
                text = stringResource(
                    if (pagerState.currentPage < pages.size - 1) R.string.btn_next else R.string.btn_start
                ),
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else onComplete()
                }
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val titleRes: Int,
    val descRes: Int
)
