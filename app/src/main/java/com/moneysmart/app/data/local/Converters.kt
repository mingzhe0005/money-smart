package com.moneysmart.app.data.local

import androidx.room.TypeConverter
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.data.local.entity.NotificationProcessStatus
import com.moneysmart.app.data.local.entity.RecurrenceFrequency
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType

class Converters {
    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromCategoryType(value: CategoryType): String = value.name

    @TypeConverter
    fun toCategoryType(value: String): CategoryType = CategoryType.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(value)

    @TypeConverter
    fun fromNotificationProcessStatus(value: NotificationProcessStatus): String = value.name

    @TypeConverter
    fun toNotificationProcessStatus(value: String): NotificationProcessStatus =
        NotificationProcessStatus.valueOf(value)
}
