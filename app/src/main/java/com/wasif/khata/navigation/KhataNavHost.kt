package com.wasif.khata.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.ui.motion.fadeThroughEnter
import com.wasif.khata.core.ui.motion.fadeThroughExit
import com.wasif.khata.core.ui.motion.isHubTransition
import com.wasif.khata.core.ui.motion.sharedAxisXEnter
import com.wasif.khata.core.ui.motion.sharedAxisXExit
import com.wasif.khata.core.ui.motion.sharedAxisXPopEnter
import com.wasif.khata.core.ui.motion.sharedAxisXPopExit
import com.wasif.khata.core.ui.theme.LocalMotion
import com.wasif.khata.feature.editor.TransactionEditorScreen
import com.wasif.khata.feature.editor.TransactionEditorViewModel
import com.wasif.khata.feature.hub.ModulesScreen
import com.wasif.khata.feature.ledger.LedgerScreen
import com.wasif.khata.feature.reconcile.DriftScreen
import com.wasif.khata.feature.ruleeditor.RuleEditorScreen
import com.wasif.khata.feature.ruleeditor.RuleEditorViewModel
import com.wasif.khata.feature.unmatched.UnmatchedScreen
import com.wasif.khata.feature.settings.SettingsScreen
import com.wasif.khata.feature.wallet.WalletScreen

object KhataRoutes {
    const val Modules = "modules"
    const val Wallet = "wallet"
    const val Ledger = "ledger"
    const val Settings = "settings"
    const val Unmatched = "unmatched"
    const val Reconcile = "reconcile"
    const val RuleEditor = "rules/new/{rawMessageId}"
    const val EditorNew = "editor/new"
    const val EditorEdit = "editor/edit/{transactionId}"
    const val ArgTransactionId = "transactionId"
    const val ArgRawMessageId = "rawMessageId"

    fun editorEdit(id: Long): String = "editor/edit/$id"

    fun ruleEditor(rawMessageId: Long): String = "rules/new/$rawMessageId"
}

@Composable
fun KhataNavHost(homeView: HomeView) {
    val navController = rememberNavController()

    // The preference IS the back-stack root, which is why it has to be resolved
    // before this composes: back from the root exits the app, and that cannot
    // be changed once the graph is built.
    //
    // I6: `homeView` is a live parameter -- MainActivity recomposes this on
    // every DataStore emission, including the one the user just caused by
    // changing the very preference read here. NavHost rebuilds its graph
    // whenever `startDestination` changes and calls `setGraph` unconditionally,
    // which pops the live back stack and ejects the user to the new root
    // mid-interaction (confirmed against navigation-compose 2.10.0). `remember`
    // with no keys captures `homeView` only on this composable's first
    // composition and ignores every later value, so the graph -- and the
    // comment's promise above -- both hold for the rest of the process.
    // Settings tells the user the new value takes effect next launch.
    val frozenHomeView = remember { homeView }
    val start = when (frozenHomeView) {
        HomeView.Modules -> KhataRoutes.Modules
        HomeView.Wallet -> KhataRoutes.Wallet
    }

    val motion = LocalMotion.current

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXEnter(motion) else fadeThroughEnter(motion)
        },
        exitTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXExit(motion) else fadeThroughExit(motion)
        },
        popEnterTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXPopEnter(motion) else fadeThroughEnter(motion)
        },
        popExitTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXPopExit(motion) else fadeThroughExit(motion)
        },
    ) {
        composable(KhataRoutes.Modules) {
            ModulesScreen(
                onOpenWallet = {
                    // A Wallet entry already sits beneath Modules whenever Modules was
                    // reached via the hub glyph (Wallet-as-root case). Popping up to it
                    // instead of pushing a second one keeps that entry the sole owner of
                    // "root", so isRoot below stays true for it instead of drifting false.
                    navController.navigate(KhataRoutes.Wallet) {
                        popUpTo(KhataRoutes.Wallet) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(KhataRoutes.Settings) },
            )
        }

        composable(KhataRoutes.Wallet) {
            // Root-ness is where the user is, not how they got here: a preference only
            // decides the *start* destination, but this entry can also be reached by a
            // push (hub -> Wallet card), which the preference can't distinguish.
            val isRoot = navController.previousBackStackEntry == null
            WalletScreen(
                // Back only exists when something pushed this screen. At the
                // root it would exit the app, which is not what a back arrow
                // promises.
                onBack = if (isRoot) null else ({ navController.popBackStack() }),
                onOpenHub = if (isRoot) ({ navController.navigate(KhataRoutes.Modules) }) else null,
                onOpenLedger = { navController.navigate(KhataRoutes.Ledger) },
            )
        }

        composable(KhataRoutes.Ledger) {
            LedgerScreen(
                onBack = { navController.popBackStack() },
                onAddTransaction = { navController.navigate(KhataRoutes.EditorNew) },
                onOpenTransaction = { id -> navController.navigate(KhataRoutes.editorEdit(id)) },
            )
        }

        composable(KhataRoutes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenUnmatched = { navController.navigate(KhataRoutes.Unmatched) },
                onOpenReconcile = { navController.navigate(KhataRoutes.Reconcile) },
            )
        }

        composable(KhataRoutes.Unmatched) {
            UnmatchedScreen(
                onBack = { navController.popBackStack() },
                onWriteRule = { rawId -> navController.navigate(KhataRoutes.ruleEditor(rawId)) },
            )
        }

        composable(
            route = KhataRoutes.RuleEditor,
            arguments = listOf(navArgument(KhataRoutes.ArgRawMessageId) { type = NavType.LongType }),
        ) { entry ->
            val rawId = entry.arguments?.getLong(KhataRoutes.ArgRawMessageId) ?: 0L
            RuleEditorScreen(
                onBack = { navController.popBackStack() },
                // Back to the list, not to the message just handled: it is no
                // longer unread, so returning to it would show a dead end.
                onSaved = { navController.popBackStack() },
                viewModel = hiltViewModel<RuleEditorViewModel, RuleEditorViewModel.Factory>(
                    creationCallback = { factory -> factory.create(rawId) },
                ),
            )
        }

        composable(KhataRoutes.Reconcile) {
            DriftScreen(onBack = { navController.popBackStack() })
        }

        composable(KhataRoutes.EditorNew) {
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(transactionId = null),
            )
        }

        composable(
            route = KhataRoutes.EditorEdit,
            arguments = listOf(navArgument(KhataRoutes.ArgTransactionId) { type = NavType.LongType }),
        ) { entry ->
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(entry.arguments?.getLong(KhataRoutes.ArgTransactionId)),
            )
        }
    }
}

@Composable
private fun editorViewModel(transactionId: Long?): TransactionEditorViewModel =
    hiltViewModel<TransactionEditorViewModel, TransactionEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(transactionId) },
    )
