package dev.raiseexception.odin.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
