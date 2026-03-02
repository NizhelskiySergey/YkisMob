package com.ykis.mob.di


import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ykis.mob.MainApplication
import com.ykis.mob.data.ApartmentRepositoryImpl
import com.ykis.mob.data.FamilyRepositoryImpl
import com.ykis.mob.data.HeatMeterRepositoryImpl
import com.ykis.mob.data.HeatReadingRepositoryImpl
import com.ykis.mob.data.PaymentRepositoryImpl
import com.ykis.mob.data.ServiceRepositoryImpl
import com.ykis.mob.data.WaterMeterRepositoryImpl
import com.ykis.mob.data.WaterReadingRepositoryImpl
import com.ykis.mob.data.cache.apartment.ApartmentCache
import com.ykis.mob.data.cache.apartment.ApartmentCacheImpl
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.cache.family.FamilyCache
import com.ykis.mob.data.cache.family.FamilyCacheImpl
import com.ykis.mob.data.cache.heat.meter.HeatMeterCache
import com.ykis.mob.data.cache.heat.meter.HeatMeterCacheImpl
import com.ykis.mob.data.cache.heat.reading.HeatReadingCache
import com.ykis.mob.data.cache.heat.reading.HeatReadingCacheImpl
import com.ykis.mob.data.cache.payment.PaymentCache
import com.ykis.mob.data.cache.payment.PaymentCacheImpl
import com.ykis.mob.data.cache.preferences.AppSettingsRepository
import com.ykis.mob.data.cache.preferences.AppSettingsRepositoryImpl
import com.ykis.mob.data.cache.service.ServiceCache
import com.ykis.mob.data.cache.service.ServiceCacheImpl
import com.ykis.mob.data.cache.water.meter.WaterMeterCache
import com.ykis.mob.data.cache.water.meter.WaterMeterCacheImpl
import com.ykis.mob.data.cache.water.reading.WaterReadingCache
import com.ykis.mob.data.cache.water.reading.WaterReadingCacheImpl
import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.data.remote.api.ApiService.Companion.BASE_URL
import com.ykis.mob.data.remote.appartment.ApartmentRemote
import com.ykis.mob.data.remote.appartment.ApartmentRemoteImpl
import com.ykis.mob.data.remote.core.NetworkHandler
import com.ykis.mob.data.remote.family.FamilyRemote
import com.ykis.mob.data.remote.family.FamilyRemoteImpl
import com.ykis.mob.data.remote.heat.meter.HeatMeterRemote
import com.ykis.mob.data.remote.heat.meter.HeatMeterRemoteImpl
import com.ykis.mob.data.remote.heat.reading.HeatReadingRemote
import com.ykis.mob.data.remote.heat.reading.HeatReadingRemoteImpl
import com.ykis.mob.data.remote.payment.PaymentRemote
import com.ykis.mob.data.remote.payment.PaymentRemoteImpl
import com.ykis.mob.data.remote.service.ServiceRemote
import com.ykis.mob.data.remote.service.ServiceRemoteImpl
import com.ykis.mob.data.remote.water.meter.WaterMeterRemote
import com.ykis.mob.data.remote.water.meter.WaterMeterRemoteImpl
import com.ykis.mob.data.remote.water.reading.WaterReadingRemote
import com.ykis.mob.data.remote.water.reading.WaterReadingRemoteImpl
import com.ykis.mob.domain.ClearDatabase
import com.ykis.mob.domain.apartment.ApartmentRepository
import com.ykis.mob.domain.apartment.request.AddApartment
import com.ykis.mob.domain.apartment.request.DeleteApartment
import com.ykis.mob.domain.apartment.request.GetApartment
import com.ykis.mob.domain.apartment.request.GetApartmentList
import com.ykis.mob.domain.apartment.request.UpdateBti
import com.ykis.mob.domain.family.FamilyRepository
import com.ykis.mob.domain.family.request.GetFamilyList
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import com.ykis.mob.domain.meter.heat.meter.request.GetHeatMeterList
import com.ykis.mob.domain.meter.heat.reading.HeatReadingRepository
import com.ykis.mob.domain.meter.heat.reading.request.AddHeatReading
import com.ykis.mob.domain.meter.heat.reading.request.DeleteLastHeatReading
import com.ykis.mob.domain.meter.heat.reading.request.GetHeatReadings
import com.ykis.mob.domain.meter.heat.reading.request.GetLastHeatReading
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.meter.request.GetWaterMeterList
import com.ykis.mob.domain.meter.water.reading.WaterReadingRepository
import com.ykis.mob.domain.meter.water.reading.request.AddWaterReading
import com.ykis.mob.domain.meter.water.reading.request.DeleteLastWaterReading
import com.ykis.mob.domain.meter.water.reading.request.GetLastWaterReading
import com.ykis.mob.domain.meter.water.reading.request.GetWaterReadings
import com.ykis.mob.domain.payment.PaymentRepository
import com.ykis.mob.domain.payment.request.GetPaymentList
import com.ykis.mob.domain.payment.request.InsertPayment
import com.ykis.mob.domain.service.ServiceRepository
import com.ykis.mob.domain.service.request.GetFlatServices
import com.ykis.mob.domain.service.request.GetTotalDebtServices
import com.ykis.mob.firebase.service.impl.ConfigurationServiceImpl
import com.ykis.mob.firebase.service.impl.FirebaseServiceImpl
import com.ykis.mob.firebase.service.impl.LogServiceImpl
import com.ykis.mob.firebase.service.repo.ConfigurationService
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.auth.sign_in.SignInViewModel
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
import com.ykis.mob.ui.screens.chat.ChatViewModel
import com.ykis.mob.ui.screens.family.FamilyListViewModel
import com.ykis.mob.ui.screens.meter.MeterViewModel
import com.ykis.mob.ui.screens.profile.ProfileViewModel
import com.ykis.mob.ui.screens.service.ServiceViewModel
import com.ykis.mob.ui.screens.settings.NewSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory


val appModule = module {
  single {
    Moshi.Builder()
      .add(KotlinJsonAdapterFactory()) // Не забудьте эту строку для работы с Kotlin-классами
      .build()
  }
  // Logging Interceptor
  single {
    HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BODY
    }
  }

  // OkHttpClient
  single {
    OkHttpClient.Builder()
      .addInterceptor(get<HttpLoggingInterceptor>())
      .build()
  }

  // Retrofit
  single {
    Retrofit.Builder()
      .addConverterFactory(MoshiConverterFactory.create(get()))
      .baseUrl(BASE_URL) // Замените на константу
      .client(get())
      .build()
  }

  // ApiService
  single { get<Retrofit>().create(ApiService::class.java) }
  single { NetworkHandler(androidContext()) }
  // Database
  single {
    Room.databaseBuilder(
      androidContext(),
      AppDatabase::class.java,
      AppDatabase.DATABASE_NAME
    )
      // Добавьте это, чтобы избежать блокировки при первом обращении
      .setQueryCallback({ sqlQuery, bindArgs ->
        // Опционально для дебага
      }, { it.run() })
      .build()
  }

  // Вместо прямой инъекции базы в ClearDatabase, передавайте её лениво
//  single { ClearDatabase(getKoin().inject<AppDatabase>()) }
  factory { ClearDatabase() }
}
val domainModule = module {
  // Use Case Apartment
  factory { GetApartmentList(get(),get()) }
  factory { GetApartment(get(),get()) }
  factory { DeleteApartment(get(),get()) }
  factory { AddApartment(get()) }
  factory { UpdateBti(get())}
  factory { GetFamilyList(get(),get())}

  // Use Cases для воды
  factory { GetWaterMeterList(get(), get()) }
  factory { GetLastWaterReading(get(), get()) }
  factory { GetWaterReadings(get(), get()) }
  factory { AddWaterReading(get(), get()) }
  factory { DeleteLastWaterReading(get(), get()) }

  // Use Cases для тепла
  factory { GetHeatMeterList(get(), get()) }
  factory { GetHeatReadings(get(), get()) }
  factory { GetLastHeatReading(get(), get()) }
  factory { AddHeatReading(get(), get()) }
  factory { DeleteLastHeatReading(get(), get()) }

  // Регистрация Use Cases для экрана сервисов и оплат
  factory { GetFlatServices(get(), get()) }
  factory { GetTotalDebtServices(get(), get()) }
  factory { GetPaymentList(get(), get()) }
  factory { InsertPayment(get()) }
  single<LogService> { LogServiceImpl() }
}
val dataModule = module {
  // 1. Scope
  single { CoroutineScope(SupervisorJob()) }

  // 2. DAOs (используем ранее созданный AppDatabase)

  single { get<AppDatabase>().apartmentDao() }
  single { get<AppDatabase>().familyDao() }
  single { get<AppDatabase>().serviceDao() }
  single { get<AppDatabase>().paymentDao() }
  single { get<AppDatabase>().waterMeterDao() }
  single { get<AppDatabase>().waterReadingDao() }
  single { get<AppDatabase>().heatMeterDao() }
  single { get<AppDatabase>().heatReadingDao() }

  // 3. Repositories
  single<ApartmentRepository> { ApartmentRepositoryImpl(get()) }
  single<FamilyRepository> { FamilyRepositoryImpl(get()) }
  single<ServiceRepository> { ServiceRepositoryImpl(get()) }
  single<PaymentRepository> { PaymentRepositoryImpl(get()) }
  single<WaterMeterRepository> { WaterMeterRepositoryImpl(get()) }
  single<WaterReadingRepository> { WaterReadingRepositoryImpl(get()) }
  single<HeatMeterRepository> { HeatMeterRepositoryImpl(get()) }
  single<HeatReadingRepository> { HeatReadingRepositoryImpl(get()) }
  single<ApartmentCache> { ApartmentCacheImpl(get()) }
  single<FamilyCache> { FamilyCacheImpl(get()) }
  single<HeatMeterCache> { HeatMeterCacheImpl(get()) }
  single<HeatReadingCache> { HeatReadingCacheImpl(get()) }
  single<PaymentCache> { PaymentCacheImpl(get()) }
  single<ServiceCache> { ServiceCacheImpl(get()) }
  single<WaterMeterCache> { WaterMeterCacheImpl(get()) }
  single<WaterReadingCache> { WaterReadingCacheImpl(get()) }
  single<ApartmentCache> { ApartmentCacheImpl(get()) }
  single<ApartmentRemote> { ApartmentRemoteImpl(get()) }
  single<FamilyRemote> { FamilyRemoteImpl(get()) }
  single<HeatMeterRemote> { HeatMeterRemoteImpl(get()) }
  single<HeatReadingRemote> { HeatReadingRemoteImpl(get()) }
  single<PaymentRemote> { PaymentRemoteImpl(get()) }
  single<ServiceRemote> { ServiceRemoteImpl(get()) }
  single<WaterMeterRemote> { WaterMeterRemoteImpl(get()) }
  single<WaterReadingRemote> { WaterReadingRemoteImpl(get()) }
  single<ServiceRemote> { ServiceRemoteImpl(get()) }
  // 4. DataStore
  single<AppSettingsRepository> { AppSettingsRepositoryImpl(androidContext()) }
}

val firebaseModule = module {
  // 1. Оставляем только быстрые инстансы Firebase
  single { Firebase.auth }
  single { Firebase.firestore }
  single { FirebaseDatabase.getInstance() } // Realtime Database
  single { FirebaseStorage.getInstance() }  // Storage
  single { FirebaseFunctions.getInstance() } // Cloud Functions
  single { FirebaseCrashlytics.getInstance() }
  // 2. Ваши вспомогательные сервисы

  single<ConfigurationService> { ConfigurationServiceImpl() }
  single {
    Firebase.ai.generativeModel(
      modelName = "gemini-2.5-flash-lite",
      systemInstruction = content {
        text("""
                You are the official AI assistant for a residential complex.
                Your knowledge base is limited to the following rules:
                1. Answer only questions about housing and utilities, tariffs, and life in the building.
                2. If you are asked about something unrelated (politics, games, personal advice), politely answer that you only help with housing association issues.
                3. Tone of communication: polite, official, but brief.
                4. If you do not know the exact answer (for example, there is no water), advise you to contact the dispatcher at +380000000000.
            """.trimIndent())
      }
    )

  }
  // 3. Единственный сервис авторизации (теперь он легкий)
  single<FirebaseService> { FirebaseServiceImpl(context = androidContext()) }
}
val viewModelsModule = module {
  single { androidApplication() as MainApplication }


  viewModel {
    NewSettingsViewModel(
      dataStore = get(),
      application = get(), // Теперь get() сам найдет MainApplication, зарегистрированный строкой выше
      clearDatabase = get(),
      firebaseService = get()
    )
  }


  viewModel {
    ApartmentViewModel(
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get(), get()
    )
  }
  viewModel { FamilyListViewModel(get(), get()) }
  viewModel { ChatViewModel(
    get(),
    get(),
    get(),
    get(),
    get(),
    get()) }
  viewModel { SignInViewModel(get(), get()) }
  viewModel { SignUpViewModel(get(), get(), get()) }
  viewModel {
    MeterViewModel(
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get()
    )
  }
  viewModel { ProfileViewModel(get(), get(), get()) }
  viewModel { ServiceViewModel(get(), get(), get(), get(), get()) }

}

