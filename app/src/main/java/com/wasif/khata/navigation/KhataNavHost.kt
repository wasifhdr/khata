package com.wasif.khata.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.feature.editor.TransactionEditorScreen
import com.wasif.khata.feature.editor.TransactionEditorViewModel
import com.wasif.khata.feature.hub.ModulesScreen
import com.wasif.khata.feature.ledger.LedgerScreen
import com.wasif.khata.feature.settings.SettingsScreen
import com.wasif.khata.feature.wallet.WalletScreen

object KhataRoutes {
    const val Modules = "modules"
    const val Wallet = "wallet"
    const val Ledger = "ledger"
    const val Settings = "settings"
    const val EditorNew = "editor/new"
    const val EditorEdit = "editor/edit/{transactionId}"
    const val ArgTransactionId = "transactionId"

    fun editorEdit(id: Long): String = "editor/edit/$id"
}

@Composable
fun KhataNavHost(homeView: HomeView) {
    val navController = rememberNavController()

    // The preference IS the back-stack root, which is why it has to be resolved
    // before this composes: back from the root exits the app, and that cannot
    // be changed once the graph is built.
    val start = when (homeView) {
        HomeView.Modules -> KhataRoutes.Modules
        HomeView.Wallet -> KhataRoutes.Wallet
    }

    NavHost(navController = navController, startDestination = start) {
        composable(KhataRoutes.Modules) {
            ModulesScreen(
                onOpenWallet = { navController.navigate(KhataRoutes.Wallet) },
                onOpenSettings = { navController.navigate(KhataRoutes.Settings) },
            )
        }

        composable(KhataRoutes.Wallet) {
            WalletScreen(
                onBack = { navController.popBackStack() },
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
            SettingsScreen(onBack = { navController.popBackStack() })
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
