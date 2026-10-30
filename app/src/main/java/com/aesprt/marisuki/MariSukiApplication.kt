package com.aesprt.marisuki

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.AppPreferences
import com.aesprt.marisuki.data.EncryptedBackupManager
import com.aesprt.marisuki.data.ProductImageStore
import com.aesprt.marisuki.data.AutomaticBackupWorker
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.data.auth.CredentialSecurity
import com.aesprt.marisuki.data.auth.EncryptedSessionStore
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.data.local.PosStoragePaths
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.feature.auth.AuthViewModel
import com.aesprt.marisuki.feature.auth.StaffAdminViewModel
import com.aesprt.marisuki.domain.auth.PosAccessControl
import com.aesprt.marisuki.domain.auth.RestrictedActionApproval
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class MariSukiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MariSukiApplication)
            modules(posModule)
        }
        installVariantDemoData(this)
        val automaticBackup = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "marisuki-automatic-backup",
            ExistingPeriodicWorkPolicy.KEEP,
            automaticBackup,
        )
    }
}

private val posModule = module {
    single {
        val context = androidContext()
        val name = if (BuildConfig.DEBUG) "marisuki-pos-debug.db" else "marisuki-pos.db"
        Room.databaseBuilder(context, PosDatabase::class.java, PosStoragePaths.databasePath(context, name))
            .addMigrations(PosDatabase.MIGRATION_1_2)
            .addMigrations(
                PosDatabase.MIGRATION_2_3,
                PosDatabase.MIGRATION_3_4,
                PosDatabase.MIGRATION_4_5,
                PosDatabase.MIGRATION_5_6,
                PosDatabase.MIGRATION_6_7,
                PosDatabase.MIGRATION_7_8,
            )
            .build()
    }
    single { get<PosDatabase>().posDao() }
    single { AppPreferences(androidContext()) }
    single { ProductImageStore(androidContext()) }
    single { CredentialSecurity() }
    single { EncryptedSessionStore(androidContext()) }
    single { AuthRepository(get(), get(), get(), get(), get()) }
    single<PosAccessControl> { get<AuthRepository>() }
    single<RestrictedActionApproval> { get<AuthRepository>() }
    single { PosRepository(get(), get(), get()) }
    single { EncryptedBackupManager(androidContext(), get(), get(), get(), get()) }
    single { com.aesprt.marisuki.feature.shift.EndShiftReportPdfExporter(androidContext()) }
    viewModel { PosViewModel(get(), get(), get(), get(), get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { StaffAdminViewModel(get()) }
}
