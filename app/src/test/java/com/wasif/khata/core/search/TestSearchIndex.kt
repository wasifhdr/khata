package com.wasif.khata.core.search

import com.wasif.khata.core.data.KhataDatabase

/**
 * The real index over a test database, rather than a fake. Search is the thing under
 * test in half these cases, and a fake would agree with whatever it was told.
 */
fun searchIndex(db: KhataDatabase) = SearchIndex(
    sources = setOf(
        TransactionIndexSource(db.transactionDao(), db.merchantDao(), db.tagDao()),
        RestaurantIndexSource(db.restaurantDao(), db.placeDao(), db.tagDao()),
        VehicleServiceIndexSource(db.vehicleDao(), db.placeDao(), db.tagDao()),
        TitleIndexSource(db.watchlistDao(), db.tagDao()),
    ),
    dao = db.searchDao(),
)
