package com.wasif.khata.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wasif.khata.feature.editor.TransactionEditorScreen
import com.wasif.khata.feature.editor.TransactionEditorViewModel
import com.wasif.khata.feature.ledger.LedgerScreen

private const val ROUTE_LEDGER = "ledger"
private const val ROUTE_EDITOR_NEW = "editor/new"
private const val ROUTE_EDITOR_EDIT = "editor/edit/{transactionId}"
private const val ARG_TRANSACTION_ID = "transactionId"

@Composable
fun KhataNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LEDGER) {
        composable(ROUTE_LEDGER) {
            LedgerScreen(
                onAddTransaction = { navController.navigate(ROUTE_EDITOR_NEW) },
                onOpenTransaction = { id -> navController.navigate("editor/edit/$id") },
            )
        }

        composable(ROUTE_EDITOR_NEW) {
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(transactionId = null),
            )
        }

        composable(
            route = ROUTE_EDITOR_EDIT,
            arguments = listOf(navArgument(ARG_TRANSACTION_ID) { type = NavType.LongType }),
        ) { entry ->
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(entry.arguments?.getLong(ARG_TRANSACTION_ID)),
            )
        }
    }
}

@Composable
private fun editorViewModel(transactionId: Long?): TransactionEditorViewModel =
    hiltViewModel<TransactionEditorViewModel, TransactionEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(transactionId) },
    )
