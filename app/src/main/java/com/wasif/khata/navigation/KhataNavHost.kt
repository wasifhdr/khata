package com.wasif.khata.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.compose.runtime.LaunchedEffect
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
import com.wasif.khata.feature.categories.CategoriesScreen
import com.wasif.khata.feature.hub.ModulesScreen
import com.wasif.khata.feature.insights.InsightsScreen
import com.wasif.khata.feature.ledger.LedgerScreen
import com.wasif.khata.feature.owed.OwedScreen
import com.wasif.khata.feature.reconcile.DriftScreen
import com.wasif.khata.feature.ruleeditor.RuleEditorScreen
import com.wasif.khata.feature.ruleeditor.RuleEditorViewModel
import com.wasif.khata.feature.restaurants.AddToWishlistScreen
import com.wasif.khata.feature.restaurants.RestaurantScreen
import com.wasif.khata.feature.restaurants.RestaurantViewModel
import com.wasif.khata.feature.restaurants.RestaurantsScreen
import com.wasif.khata.feature.restaurants.VisitEditorScreen
import com.wasif.khata.feature.restaurants.VisitEditorViewModel
import com.wasif.khata.feature.vehicle.CostsScreen
import com.wasif.khata.feature.vehicle.ServiceEditorScreen
import com.wasif.khata.feature.vehicle.ServiceEditorViewModel
import com.wasif.khata.feature.vehicle.ServiceScreen
import com.wasif.khata.feature.vehicle.ServiceViewModel
import com.wasif.khata.feature.vehicle.VehicleScreen
import com.wasif.khata.feature.watchlist.AddTitleScreen
import com.wasif.khata.feature.watchlist.TitleScreen
import com.wasif.khata.feature.watchlist.TitleViewModel
import com.wasif.khata.feature.watchlist.WatchlistScreen
import com.wasif.khata.feature.search.SearchScreen
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
    const val Owed = "owed"
    const val Insights = "insights"
    const val Categories = "categories"
    const val RuleEditor = "rules/new/{rawMessageId}"
    const val Restaurants = "restaurants"
    const val Restaurant = "restaurants/{restaurantId}"
    const val VisitNew = "restaurants/visit/new?restaurantId={restaurantId}"
    const val VisitEdit = "restaurants/visit/{visitId}"
    const val Wishlist = "wishlist"
    const val Vehicle = "vehicle"
    const val VehicleService = "vehicle/service/{serviceId}"
    const val VehicleServiceNew = "vehicle/service/new"
    const val VehicleServiceEdit = "vehicle/service/{serviceId}/edit"
    const val VehicleCosts = "vehicle/costs"
    const val Watchlist = "watchlist"
    const val Title = "watchlist/{titleId}"
    const val AddTitle = "watchlist/add"
    const val ArgTitleId = "titleId"
    const val ArgServiceId = "serviceId"
    const val Search = "search"
    const val ArgRestaurantId = "restaurantId"
    const val ArgVisitId = "visitId"
    const val EditorNew = "editor/new"
    const val EditorEdit = "editor/edit/{transactionId}"
    const val ArgTransactionId = "transactionId"
    const val ArgRawMessageId = "rawMessageId"

    fun editorEdit(id: Long): String = "editor/edit/$id"

    fun ruleEditor(rawMessageId: Long): String = "rules/new/$rawMessageId"

    fun restaurant(id: Long): String = "restaurants/$id"

    fun visitEdit(id: Long): String = "restaurants/visit/$id"

    fun vehicleService(id: Long): String = "vehicle/service/$id"

    fun title(id: Long): String = "watchlist/$id"

    /** -1 is "no restaurant yet", which is the visit-first case. */
    fun visitNew(restaurantId: Long = -1L): String = "restaurants/visit/new?restaurantId=$restaurantId"
}

@Composable
fun KhataNavHost(
    homeView: HomeView,
    sharedPlaceText: String? = null,
    settleTransactionId: Long? = null,
) {
    val navController = rememberNavController()

    // The notification's "Own transfer" opens the app with a transaction on it. Wallet
    // is where that question is answered, and it may not be the start destination.
    LaunchedEffect(settleTransactionId) {
        if (settleTransactionId != null) navController.navigate(KhataRoutes.Wallet)
    }

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
    // A share is what this launch is for, so it is the root rather than something
    // pushed onto one: back from it leaves the app and returns the user to Maps,
    // which is where they came from.
    val start = when {
        sharedPlaceText != null -> KhataRoutes.Wishlist
        frozenHomeView == HomeView.Wallet -> KhataRoutes.Wallet
        else -> KhataRoutes.Modules
    }

    val motion = LocalMotion.current
    val crossesHub: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean = {
        isHubTransition(initialState.destination.route, targetState.destination.route)
    }

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { if (crossesHub()) sharedAxisXEnter(motion) else fadeThroughEnter(motion) },
        exitTransition = { if (crossesHub()) sharedAxisXExit(motion) else fadeThroughExit(motion) },
        popEnterTransition = { if (crossesHub()) sharedAxisXPopEnter(motion) else fadeThroughEnter(motion) },
        popExitTransition = { if (crossesHub()) sharedAxisXPopExit(motion) else fadeThroughExit(motion) },
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
                onOpenSearch = { navController.navigate(KhataRoutes.Search) },
                onOpenRestaurants = { navController.navigate(KhataRoutes.Restaurants) },
                onOpenVehicle = { navController.navigate(KhataRoutes.Vehicle) },
                onOpenWatchlist = { navController.navigate(KhataRoutes.Watchlist) },
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
                onOpenOwed = { navController.navigate(KhataRoutes.Owed) },
                onOpenInsights = { navController.navigate(KhataRoutes.Insights) },
                initialSettleId = settleTransactionId,
            )
        }

        composable(KhataRoutes.Ledger) {
            LedgerScreen(
                onBack = { navController.popBackStack() },
                onAddTransaction = { navController.navigate(KhataRoutes.EditorNew) },
                onOpenTransaction = { id -> navController.navigate(KhataRoutes.editorEdit(id)) },
            )
        }

        composable(KhataRoutes.Wishlist) {
            AddToWishlistScreen(
                onBack = { if (!navController.popBackStack()) navController.navigate(KhataRoutes.Modules) },
                onSaved = { id ->
                    navController.navigate(KhataRoutes.restaurant(id)) {
                        popUpTo(KhataRoutes.Wishlist) { inclusive = true }
                    }
                },
                onLogVisitInstead = { navController.navigate(KhataRoutes.visitNew()) },
                sharedText = sharedPlaceText,
            )
        }

        composable(KhataRoutes.Restaurants) {
            RestaurantsScreen(
                onBack = { navController.popBackStack() },
                onLogVisit = { navController.navigate(KhataRoutes.visitNew()) },
                onOpenRestaurant = { id -> navController.navigate(KhataRoutes.restaurant(id)) },
                onAddToWishlist = { navController.navigate(KhataRoutes.Wishlist) },
            )
        }

        composable(
            route = KhataRoutes.Restaurant,
            arguments = listOf(navArgument(KhataRoutes.ArgRestaurantId) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(KhataRoutes.ArgRestaurantId) ?: 0L
            RestaurantScreen(
                onBack = { navController.popBackStack() },
                onLogVisit = { restaurantId ->
                    navController.navigate(KhataRoutes.visitNew(restaurantId))
                },
                onOpenVisit = { visitId -> navController.navigate(KhataRoutes.visitEdit(visitId)) },
                viewModel = hiltViewModel<RestaurantViewModel, RestaurantViewModel.Factory>(
                    key = "restaurant-$id",
                    creationCallback = { factory -> factory.create(id) },
                ),
            )
        }

        composable(
            route = KhataRoutes.VisitNew,
            arguments = listOf(
                navArgument(KhataRoutes.ArgRestaurantId) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val restaurantId = entry.arguments?.getLong(KhataRoutes.ArgRestaurantId) ?: -1L
            VisitEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = visitEditorViewModel(
                    visitId = null,
                    restaurantId = restaurantId.takeIf { it > 0 },
                ),
            )
        }

        composable(
            route = KhataRoutes.VisitEdit,
            arguments = listOf(navArgument(KhataRoutes.ArgVisitId) { type = NavType.LongType }),
        ) { entry ->
            VisitEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = visitEditorViewModel(
                    visitId = entry.arguments?.getLong(KhataRoutes.ArgVisitId),
                    restaurantId = null,
                ),
            )
        }

        composable(KhataRoutes.Vehicle) {
            VehicleScreen(
                onBack = { navController.popBackStack() },
                onOpenService = { id -> navController.navigate(KhataRoutes.vehicleService(id)) },
                onLogService = { navController.navigate(KhataRoutes.VehicleServiceNew) },
                onOpenCosts = { navController.navigate(KhataRoutes.VehicleCosts) },
            )
        }

        composable(KhataRoutes.VehicleServiceNew) {
            ServiceEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = serviceEditorViewModel(serviceId = null),
            )
        }

        composable(
            route = KhataRoutes.VehicleService,
            arguments = listOf(navArgument(KhataRoutes.ArgServiceId) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(KhataRoutes.ArgServiceId) ?: 0L
            ServiceScreen(
                onBack = { navController.popBackStack() },
                onEdit = { serviceId -> navController.navigate(KhataRoutes.vehicleService(serviceId) + "/edit") },
                viewModel = hiltViewModel<ServiceViewModel, ServiceViewModel.Factory>(
                    key = "service-$id",
                    creationCallback = { factory -> factory.create(id) },
                ),
            )
        }

        composable(
            route = KhataRoutes.VehicleServiceEdit,
            arguments = listOf(navArgument(KhataRoutes.ArgServiceId) { type = NavType.LongType }),
        ) { entry ->
            ServiceEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = serviceEditorViewModel(serviceId = entry.arguments?.getLong(KhataRoutes.ArgServiceId)),
            )
        }

        composable(KhataRoutes.VehicleCosts) {
            CostsScreen(onBack = { navController.popBackStack() })
        }

        composable(KhataRoutes.Watchlist) {
            WatchlistScreen(
                onBack = { navController.popBackStack() },
                onOpenTitle = { id -> navController.navigate(KhataRoutes.title(id)) },
                onAddTitle = { navController.navigate(KhataRoutes.AddTitle) },
            )
        }

        // Registered before the {titleId} pattern so the literal wins outright; a
        // LongType arg would not parse "add", but the order removes the question.
        composable(KhataRoutes.AddTitle) {
            AddTitleScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = KhataRoutes.Title,
            arguments = listOf(navArgument(KhataRoutes.ArgTitleId) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(KhataRoutes.ArgTitleId) ?: 0L
            TitleScreen(
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel<TitleViewModel, TitleViewModel.Factory>(
                    key = "title-$id",
                    creationCallback = { factory -> factory.create(id) },
                ),
                now = System.currentTimeMillis(),
            )
        }

        composable(KhataRoutes.Search) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate(KhataRoutes.editorEdit(id)) },
                onOpenRestaurant = { id -> navController.navigate(KhataRoutes.restaurant(id)) },
                onOpenService = { id -> navController.navigate(KhataRoutes.vehicleService(id)) },
                onOpenTitle = { id -> navController.navigate(KhataRoutes.title(id)) },
            )
        }

        composable(KhataRoutes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenUnmatched = { navController.navigate(KhataRoutes.Unmatched) },
                onOpenReconcile = { navController.navigate(KhataRoutes.Reconcile) },
                onOpenCategories = { navController.navigate(KhataRoutes.Categories) },
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

        composable(KhataRoutes.Categories) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }

        composable(KhataRoutes.Insights) {
            InsightsScreen(onBack = { navController.popBackStack() })
        }

        composable(KhataRoutes.Owed) {
            OwedScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate(KhataRoutes.editorEdit(id)) },
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
private fun visitEditorViewModel(visitId: Long?, restaurantId: Long?): VisitEditorViewModel =
    hiltViewModel<VisitEditorViewModel, VisitEditorViewModel.Factory>(
        key = "visit-$visitId-$restaurantId",
        creationCallback = { factory -> factory.create(visitId, restaurantId) },
    )

@Composable
private fun serviceEditorViewModel(serviceId: Long?): ServiceEditorViewModel =
    hiltViewModel<ServiceEditorViewModel, ServiceEditorViewModel.Factory>(
        key = "service-editor-$serviceId",
        creationCallback = { factory -> factory.create(serviceId) },
    )

@Composable
private fun editorViewModel(transactionId: Long?): TransactionEditorViewModel =
    hiltViewModel<TransactionEditorViewModel, TransactionEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(transactionId) },
    )
