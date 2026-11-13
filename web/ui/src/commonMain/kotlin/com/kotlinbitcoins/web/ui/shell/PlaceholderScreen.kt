package com.kotlinbitcoins.web.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.ui.components.PageHeading
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.banner_account_created
import com.kotlinbitcoins.web.ui.resources.banner_session_expired
import com.kotlinbitcoins.web.ui.resources.under_construction
import org.jetbrains.compose.resources.stringResource

/** Temporary page of a destination whose user story is not implemented yet (T092). */
@Composable
internal fun PlaceholderScreen(
    title: String,
    banner: LoginBanner? = null,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading(title)
        if (banner != null) {
            Text(
                text = stringResource(banner.text()),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(stringResource(Res.string.under_construction))
    }
}

internal fun LoginBanner.text() =
    when (this) {
        LoginBanner.SessionExpired -> Res.string.banner_session_expired
        LoginBanner.AccountCreated -> Res.string.banner_account_created
    }
