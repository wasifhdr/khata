package com.wasif.khata.core.data.seed

import com.wasif.khata.core.model.AccountType

data class SeedAccount(
    val slug: String,
    val name: String,
    val type: AccountType,
    val smsIdentifiers: String,
)

data class SeedCategory(
    val slug: String,
    val name: String,
    val icon: String,
    val colorToken: String,
)

val DEFAULT_ACCOUNTS = listOf(
    SeedAccount("bkash", "bKash", AccountType.MFS, "bKash"),
    SeedAccount("ebl", "EBL", AccountType.BANK, "EBL,EBLBANK"),
    SeedAccount("cash", "Cash", AccountType.CASH, ""),
)

val DEFAULT_CATEGORIES = listOf(
    SeedCategory("groceries", "Groceries", "shopping_cart", "category_green"),
    SeedCategory("eating_out", "Eating Out", "restaurant", "category_orange"),
    SeedCategory("transport", "Transport", "directions_car", "category_blue"),
    SeedCategory("fuel", "Fuel", "local_gas_station", "category_slate"),
    SeedCategory("bills", "Bills & Utilities", "receipt_long", "category_amber"),
    SeedCategory("mobile", "Mobile & Internet", "wifi", "category_teal"),
    SeedCategory("health", "Health", "medical_services", "category_red"),
    SeedCategory("shopping", "Shopping", "shopping_bag", "category_violet"),
    SeedCategory("entertainment", "Entertainment", "movie", "category_pink"),
    SeedCategory("education", "Education", "school", "category_indigo"),
    SeedCategory("family", "Family & Gifts", "volunteer_activism", "category_rose"),
    SeedCategory("car", "Car & Maintenance", "build", "category_bronze"),
    SeedCategory("fees", "Fees & Charges", "account_balance", "category_grey"),
    SeedCategory("income", "Income", "payments", "category_emerald"),
    SeedCategory("transfer", "Transfer", "swap_horiz", "category_neutral"),
    SeedCategory("uncategorized", "Uncategorized", "help_outline", "category_neutral"),
)
