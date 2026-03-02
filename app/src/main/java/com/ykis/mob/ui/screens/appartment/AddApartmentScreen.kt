package com.ykis.mob.ui.screens.appartment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ykis.mob.R
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.navigation.navigateToInfoApartment
import com.ykis.mob.ui.theme.YkisPAMTheme
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun AddApartmentScreenStateless(
    modifier: Modifier = Modifier,
    isButtonEnabled : Boolean,
    onDrawerClicked: () -> Unit,
    onAddClick : () -> Unit,
    navigationType: NavigationType,
    code : String,
    onCodeChanged : (String)-> Unit
                                ) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DefaultAppBar(
                navigationType = navigationType,
                onDrawerClick = onDrawerClicked,
                title = stringResource(id = R.string.add_appartment),
                canNavigateBack = false
            )
            Column(
                modifier = modifier
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
                ) {
                        Column(
                            modifier = Modifier
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(PaddingValues(8.dp)),

                                ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {

                                    Text(
                                        text = stringResource(id = R.string.tooltip_code),
                                        modifier = Modifier.padding(4.dp),
                                        textAlign = TextAlign.Left,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(PaddingValues(8.dp)),

                                ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp, end = 8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = code,
                                        onValueChange = { newText ->
                                            val filteredText = newText.filter { it.isDigit() }
                                            onCodeChanged(filteredText)
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(id = R.string.secret_сode)
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )

                                }
                                Button(
                                    onClick = { onAddClick() },
                                    enabled = isButtonEnabled,
                                    colors = ButtonDefaults.buttonColors().copy(
                                         disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_stat_name),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(if(isButtonEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        Text(text = stringResource(id = R.string.add))
                                    }
                                }
                            }
                        }
                }

            }
    }
}
@Composable
fun AddApartmentScreenContent(
    modifier: Modifier = Modifier,
    viewModel: ApartmentViewModel = koinViewModel(),
    navController : NavHostController,
    canNavigateBack : Boolean,
    onDrawerClicked : () -> Unit,
    navigationType: NavigationType,
    closeContentDetail : ()->Unit
) {

    val secretCode by viewModel.secretCode.collectAsState()
    val buttonEnabled by remember {
        derivedStateOf{
            secretCode.isNotEmpty()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current

    AddApartmentScreenStateless(
        isButtonEnabled = buttonEnabled,
        onDrawerClicked = onDrawerClicked,
        onAddClick = {
            keyboard?.hide()
            viewModel.addApartment {
                closeContentDetail()
                navController.navigateToInfoApartment()
            }
        },
        navigationType = navigationType,
        code = secretCode,
        onCodeChanged = {
            viewModel.onSecretCodeChange(it)
        }

    )
}


@Preview
@Composable
private fun AddApartmentPreview() {
    YkisPAMTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
            AddApartmentScreenStateless(
                isButtonEnabled = true,
                onDrawerClicked = {},
                onAddClick = {},
                navigationType = NavigationType.BOTTOM_NAVIGATION,
                code = "",
                onCodeChanged = {}
            )
        }
    }
}
