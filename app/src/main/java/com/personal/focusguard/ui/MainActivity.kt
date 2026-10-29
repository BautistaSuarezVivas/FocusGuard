package com.personal.focusguard.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.focusguard.data.ThemeMode
import com.personal.focusguard.detection.ShortFormTarget
import com.personal.focusguard.ui.apps.AppPickerScreen
import com.personal.focusguard.ui.dashboard.DashboardScreen
import com.personal.focusguard.ui.onboarding.OnboardingScreen
import com.personal.focusguard.ui.rules.RuleEditorScreen
import com.personal.focusguard.ui.settings.DiagnosticsScreen
import com.personal.focusguard.ui.settings.MorningAppList
import com.personal.focusguard.ui.settings.MorningAppsPickerScreen
import com.personal.focusguard.ui.settings.SettingsMenuScreen
import com.personal.focusguard.ui.settings.SettingsSection
import com.personal.focusguard.ui.settings.SettingsSectionScreen
import com.personal.focusguard.ui.theme.FocusGuardTheme
import com.personal.focusguard.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            ThemedContent(this, settings?.themeMode ?: ThemeMode.SYSTEM) { AppNavigation(vm) }
        }
    }
}

/**
 * Aplica el tema elegido (sistema/claro/oscuro) y ajusta los íconos de las barras del sistema
 * para que se lean bien aunque el tema de la app no coincida con el del teléfono.
 */
@Composable
fun ThemedContent(activity: ComponentActivity, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    LaunchedEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    FocusGuardTheme(mode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

private fun ruleRoute(pkg: String, label: String) = "rule/${Uri.encode(pkg)}?label=${Uri.encode(label)}"

@Composable
private fun AppNavigation(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: run {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    // Se fija una sola vez: cambiar el destino inicial de un NavHost existente reinicia la pila.
    val start = remember { if (s.onboardingDone) "home" else "onboarding" }
    NavHost(nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(vm) {
                vm.updateSettings { it.copy(onboardingDone = true) }
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("home") {
            DashboardScreen(
                vm = vm,
                onOpenSettings = { nav.navigate("settings") },
                onOpenPlatform = { target -> nav.navigate("settings/${SettingsSection.SHORT_VIDEOS.route}?expand=${target.name}") },
                onOpenMorning = { nav.navigate("settings/${SettingsSection.MORNING.route}") },
                onAddApp = { nav.navigate("apps") },
                onOpenApp = { pkg, label -> nav.navigate(ruleRoute(pkg, label)) },
            )
        }
        composable("apps") {
            AppPickerScreen(
                onBack = { nav.popBackStack() },
                onPick = { pkg, label -> nav.navigate(ruleRoute(pkg, label)) { popUpTo("apps") { inclusive = true } } },
            )
        }
        composable(
            "rule/{pkg}?label={label}",
            arguments = listOf(
                navArgument("pkg") { type = NavType.StringType },
                navArgument("label") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            RuleEditorScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsMenuScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenSection = { section -> nav.navigate("settings/${section.route}") },
            )
        }
        composable(
            "settings/{section}?expand={expand}",
            arguments = listOf(
                navArgument("section") { type = NavType.StringType },
                navArgument("expand") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            SettingsSectionScreen(
                vm = vm,
                section = SettingsSection.fromRoute(entry.arguments?.getString("section")),
                expandTarget = ShortFormTarget.entries.firstOrNull { it.name == entry.arguments?.getString("expand") },
                onPickMorningApps = { list -> nav.navigate("morning/apps/${list.route}") },
                onBack = { nav.popBackStack() },
                onOpenDiagnostics = { nav.navigate("diagnostics") },
                onOpenGuide = { nav.navigate("guide") },
            )
        }
        composable(
            "morning/apps/{list}",
            arguments = listOf(navArgument("list") { type = NavType.StringType }),
        ) { entry ->
            MorningAppsPickerScreen(
                list = MorningAppList.fromRoute(entry.arguments?.getString("list")),
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable("diagnostics") { DiagnosticsScreen(vm, onBack = { nav.popBackStack() }) }
        composable("guide") { OnboardingScreen(vm) { nav.popBackStack() } }
    }
}
