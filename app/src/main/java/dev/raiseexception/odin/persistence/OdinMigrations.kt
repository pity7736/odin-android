package dev.raiseexception.odin.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_WITH_TAGS = 3

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts_new` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`initialBalanceAmount` TEXT, " +
                "`currency` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`creditLimitAmount` TEXT, " +
                "`debtAmount` TEXT, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `accounts_new` " +
                "(`id`, `name`, `initialBalanceAmount`, `currency`, `type`, `description`, `createdAt`) " +
                "SELECT `id`, `name`, `initialBalanceAmount`, `currency`, `type`, `description`, `createdAt` " +
                "FROM `accounts`"
        )
        db.execSQL("DROP TABLE `accounts`")
        db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
    }
}

val MIGRATION_2_3 = object : Migration(2, SCHEMA_VERSION_WITH_TAGS) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tags` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`normalizedName` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalizedName` ON `tags` (`normalizedName`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expense_tags` (" +
                "`expenseId` TEXT NOT NULL, " +
                "`tagId` TEXT NOT NULL, " +
                "PRIMARY KEY(`expenseId`, `tagId`), " +
                "FOREIGN KEY(`expenseId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_tags_tagId` ON `expense_tags` (`tagId`)")
    }
}
