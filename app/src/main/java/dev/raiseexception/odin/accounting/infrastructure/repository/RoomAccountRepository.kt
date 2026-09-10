package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomAccountRepository(
    private val accountDao: AccountDao
) : AccountRepository {

    override suspend fun existsByName(name: String): Outcome<Boolean> =
        try {
            Outcome.Success(this.accountDao.existsByName(name))
        } catch (e: SQLiteException) {
            this.storageError(e.message ?: "Failed to check account name")
        }

    override suspend fun add(account: Account): Outcome<Unit> =
        try {
            this.accountDao.insert(account.toEntity())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            this.storageError(e.message ?: "Failed to add account")
        }

    override fun findById(id: String, criteria: AccountCriteria): Flow<Outcome<Account>> {
        val includeTransactions = criteria.includeIncomes || criteria.includeExpenses
        if (includeTransactions) {
            return this.accountDao.findByIdWithTransactions(id).map<_, Outcome<Account>> { accountWithTransactions ->
                if (accountWithTransactions == null) {
                    return@map this.notFoundError(id)
                }
                val (incomes, expenses) = this.splitTransactions(
                    accountWithTransactions.transactions,
                    criteria
                )
                Outcome.Success(
                    accountWithTransactions.account.toDomain(incomes, expenses)
                )
            }.catch { e ->
                if (e is SQLiteException) {
                    emit(this@RoomAccountRepository.storageError(e.message ?: "Failed to find account"))
                } else {
                    throw e
                }
            }
        }
        return this.accountDao.findById(id).map<_, Outcome<Account>> { entity ->
            if (entity == null) {
                return@map this.notFoundError(id)
            }
            Outcome.Success(entity.toDomain(emptyList(), emptyList()))
        }.catch { e ->
            if (e is SQLiteException) {
                emit(this@RoomAccountRepository.storageError(e.message ?: "Failed to find account"))
            } else {
                throw e
            }
        }
    }

    override fun getAll(criteria: AccountCriteria): Flow<Outcome<List<Account>>> {
        val includeTransactions = criteria.includeIncomes || criteria.includeExpenses
        if (includeTransactions) {
            return this.accountDao.getAllWithTransactions().map<_, Outcome<List<Account>>> { allWithTransactions ->
                Outcome.Success(
                    allWithTransactions.map { accountWithTransactions ->
                        val (incomes, expenses) = this.splitTransactions(
                            accountWithTransactions.transactions,
                            criteria
                        )
                        accountWithTransactions.account.toDomain(incomes, expenses)
                    }
                )
            }.catch { e ->
                if (e is SQLiteException) {
                    emit(this@RoomAccountRepository.storageError(e.message ?: "Failed to list accounts"))
                } else {
                    throw e
                }
            }
        }
        return this.accountDao.getAll().map<_, Outcome<List<Account>>> { entities ->
            Outcome.Success(entities.map { it.toDomain(emptyList(), emptyList()) })
        }.catch { e ->
            if (e is SQLiteException) {
                emit(this@RoomAccountRepository.storageError(e.message ?: "Failed to list accounts"))
            } else {
                throw e
            }
        }
    }

    private fun splitTransactions(
        transactions: List<TransactionEntity>,
        criteria: AccountCriteria
    ): Pair<List<Income>, List<Expense>> {
        val incomes = if (criteria.includeIncomes) {
            transactions.filter { it.type == "INCOME" }.map { it.toIncome() }
        } else {
            emptyList()
        }
        val expenses = if (criteria.includeExpenses) {
            transactions.filter { it.type == "EXPENSE" }.map { it.toExpense() }
        } else {
            emptyList()
        }
        return Pair(incomes, expenses)
    }

    private fun notFoundError(id: String): Outcome.Failure =
        Outcome.Failure(
            AccountLookupError.NotFound(
                internalMessage = "Account with id $id not found",
                externalMessage = "Cuenta no encontrada"
            )
        )

    private fun storageError(message: String): Outcome.Failure =
        Outcome.Failure(StorageError(message))
}
