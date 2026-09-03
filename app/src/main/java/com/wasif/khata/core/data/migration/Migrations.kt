package com.wasif.khata.core.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `balance_snapshots` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `accountId` INTEGER NOT NULL, " +
                "`dayIndex` INTEGER NOT NULL, `balanceMinor` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_balance_snapshots_uuid` ON `balance_snapshots` (`uuid`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_balance_snapshots_accountId_dayIndex` " +
                "ON `balance_snapshots` (`accountId`, `dayIndex`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_snapshots_dayIndex` ON `balance_snapshots` (`dayIndex`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `counterparty` TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `unexplainedMinor` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `raw_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                "`receivedAt` INTEGER NOT NULL, `bodyHash` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `matchedRuleId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_messages_uuid` ON `raw_messages` (`uuid`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_messages_sender_bodyHash_receivedAt` " +
                "ON `raw_messages` (`sender`, `bodyHash`, `receivedAt`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_messages_status` ON `raw_messages` (`status`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `parsing_rules` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `senderPattern` TEXT NOT NULL, " +
                "`bodyPattern` TEXT NOT NULL, `direction` TEXT, `kind` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, `origin` TEXT NOT NULL, " +
                "`isEnabled` INTEGER NOT NULL, `sampleMessage` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_parsing_rules_uuid` ON `parsing_rules` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsing_rules_priority` ON `parsing_rules` (`priority`)")

        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `providerTxnId` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_providerTxnId` " +
                "ON `transactions` (`providerTxnId`)"
        )

        // Rename rather than insert: existing transactions reference seed-acc-ebl by
        // id, and a fresh row would orphan every one of them.
        db.execSQL(
            "UPDATE `accounts` SET `name` = 'EBL Salary', `smsIdentifiers` = '352' " +
                "WHERE `uuid` = 'seed-acc-ebl'"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `accounts` (`uuid`, `name`, `type`, `openingBalanceMinor`, " +
                "`currentBalanceMinor`, `reportedBalanceMinor`, `reportedBalanceAt`, " +
                "`includeInNetWorth`, `smsIdentifiers`, `createdAt`, `updatedAt`, `deletedAt`) " +
                "VALUES ('seed-acc-ebl-student', 'EBL Student', 'BANK', 0, 0, NULL, NULL, 1, " +
                "'286', 0, 0, NULL)"
        )
    }
}
