package com.wasif.khata.core.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Statements copied verbatim from schemas/8.json. Room compares the result
        // against its own identity hash at open time, so anything hand-adjusted here
        // fails at runtime rather than at compile time.
        db.execSQL("CREATE TABLE IF NOT EXISTS `places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `lat` REAL, `lng` REAL, `mapsUrl` TEXT, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_places_uuid` ON `places` (`uuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `sha256` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `widthPx` INTEGER NOT NULL, `heightPx` INTEGER NOT NULL, `byteSize` INTEGER NOT NULL, `capturedAt` INTEGER, `originalUri` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_uuid` ON `media` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_sha256` ON `media` (`sha256`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `media_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, `entityType` TEXT NOT NULL, `entityId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_links_uuid` ON `media_links` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_links_mediaId` ON `media_links` (`mediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_links_entityType_entityId` ON `media_links` (`entityType`, `entityId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_uuid` ON `tags` (`uuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `tag_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tagId` INTEGER NOT NULL, `entityType` TEXT NOT NULL, `entityId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_links_uuid` ON `tag_links` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tag_links_tagId` ON `tag_links` (`tagId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tag_links_entityType_entityId` ON `tag_links` (`entityType`, `entityId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_links_tagId_entityType_entityId` ON `tag_links` (`tagId`, `entityType`, `entityId`)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `search_fts` USING FTS4(`entityType` TEXT NOT NULL, `entityId` INTEGER NOT NULL, `text` TEXT NOT NULL, tokenize=unicode61, notindexed=`entityType`, notindexed=`entityId`)")

        // The index is backfilled in SQL so search works the next time the app opens,
        // rather than after a rebuild the user has to know to trigger. Aliases and
        // tags are left out: nothing has tags yet, and SearchIndex.reindexAll covers
        // aliases the first time a transaction is written.
        db.execSQL(
            "INSERT INTO search_fts (entityType, entityId, text) " +
                "SELECT 'transaction', id, " +
                "COALESCE(merchantRaw, '') || ' ' || COALESCE(note, '') || ' ' || " +
                "COALESCE(counterparty, '') " +
                "FROM transactions WHERE deletedAt IS NULL",
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // isSystem was written true on all sixteen seeded categories and read by
        // nothing. It now means "cannot be deleted", which is true of exactly one:
        // Uncategorised is found by uuid in BudgetCarryOver and is the fallback label
        // for a transaction with no category at all.
        db.execSQL("UPDATE categories SET isSystem = 0 WHERE uuid != 'seed-cat-uncategorized'")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category_budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                "`limitMinor` INTEGER NOT NULL, `effectiveFrom` INTEGER NOT NULL, " +
                "`effectiveTo` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_budgets_uuid` ON `category_budgets` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_budgets_categoryId` ON `category_budgets` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_budgets_effectiveFrom` ON `category_budgets` (`effectiveFrom`)")
    }
}

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
