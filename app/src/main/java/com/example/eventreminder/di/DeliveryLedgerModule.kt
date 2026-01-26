package com.example.eventreminder.di

import com.example.eventreminder.data.delivery.PdfDeliveryLedger
import com.example.eventreminder.data.delivery.PdfDeliveryLedgerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeliveryLedgerModule {

    @Binds
    @Singleton
    abstract fun bindPdfDeliveryLedger(
        impl: PdfDeliveryLedgerImpl
    ): PdfDeliveryLedger
}
