package com.ykis.mob.data.remote.api

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.appartment.GetApartmentResponse
import com.ykis.mob.data.remote.appartment.GetApartmentsResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.data.remote.family.GetFamilyResponse
import com.ykis.mob.data.remote.heat.GetHeatMeterResponse
import com.ykis.mob.data.remote.heat.GetHeatReadingResponse
import com.ykis.mob.data.remote.heat.GetLastHeatReadingResponse
import com.ykis.mob.data.remote.payment.GetPaymentResponse
import com.ykis.mob.data.remote.payment.InsertPaymentResponse
import com.ykis.mob.data.remote.service.GetServiceResponse
import com.ykis.mob.data.remote.water.GetLastWaterReadingResponse
import com.ykis.mob.data.remote.water.GetWaterMeterResponse
import com.ykis.mob.data.remote.water.GetWaterReadingsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters

// ... импортируй остальные Response модели

class KtorApiService(private val client: HttpClient) {
  private val baseUrl = "https://is.yuzhny.com/YkisMobileRest/rest_api/"
//  private val baseUrl = "http://10.0.2.2/YkisPAM/YkisMobileRest/rest_api/"
//  private val baseUrl = "http://192.168.0.77:8080/YkisMobileRest/rest_api/"
//  private val baseUrl = "http://192.168.0.177/YkisPAM/YkisMobileRest/rest_api/"

  // Универсальный метод для POST FormUrlEncoded (аналог @FormUrlEncoded)
  private suspend inline fun <reified T> postForm(path: String, params: Map<String, String>): T {
    return client.submitForm(
      url = baseUrl + path,
      formParameters = parameters {
        params.forEach { (key, value) -> append(key, value) }
      }
    ) {
      // Указываем серверу и клиенту, что мы работаем с JSON
      header(HttpHeaders.Accept, ContentType.Application.Json)
    }.body()
  }


  // --- Apartment ---
  suspend fun getApartmentList(params: Map<String, String>) =
    postForm<GetApartmentsResponse>("getApartmentsByUser.php", params)



  suspend fun getApartment(params: Map<String, String>) =
    postForm<GetApartmentResponse>("getFlatById.php", params)

  suspend fun addApartment(params: Map<String, String>) =
    postForm<GetSimpleResponse>("addMyFlatByUser.php", params)

  suspend fun verifyAdminSecretWord(params: Map<String, String>) =
    postForm<GetSimpleResponse>("getSecretCode.php", params)

  suspend fun saveUserUid(params: Map<String, String>) =
    postForm<GetSimpleResponse>("saveUserUid.php", params)

  suspend fun deleteUserAccount(params: Map<String, String>) =
    postForm<GetSimpleResponse>("deleteUserAccount.php", params)

  suspend fun deleteApartment(params: Map<String, String>) =
    postForm<GetSimpleResponse>("deleteFlatByUser.php", params)

  suspend fun updateBti(params: Map<String, String>) =
    postForm<GetSimpleResponse>("updateBti.php", params)

  suspend fun getFlatById(params: Map<String, String>) =
    postForm<GetApartmentsResponse>("getFlatById.php", params)

  // --- Family ---
  suspend fun getFamilyList(params: Map<String, String>) =
    postForm<GetFamilyResponse>("getFamilyFromFlat.php", params)

  // --- Water ---
  suspend fun getWaterMeterList(params: Map<String, String>) =
    postForm<GetWaterMeterResponse>("getWaterMeter.php", params)

  suspend fun getWaterReadings(params: Map<String, String>) =
    postForm<GetWaterReadingsResponse>("getWaterReadings.php", params)

  suspend fun addWaterReading(params: Map<String, String>) =
    postForm<GetSimpleResponse>("addCurrentWaterReading.php", params)

  suspend fun deleteLastWaterReading(params: Map<String, String>) =
    postForm<GetSimpleResponse>("deleteCurrentWaterReading.php", params)

  suspend fun getLastWaterReading(params: Map<String, String>) =
    postForm<GetLastWaterReadingResponse>("getLastWaterReading.php", params)

  // --- Heat ---
  suspend fun getHeatMeterList(params: Map<String, String>) =
    postForm<GetHeatMeterResponse>("getHeatMeter.php", params)

  suspend fun getHeatReadings(params: Map<String, String>) =
    postForm<GetHeatReadingResponse>("getHeatReadings.php", params)

  suspend fun addHeatReading(params: Map<String, String>) =
    postForm<GetSimpleResponse>("addCurrentHeatReading.php", params)

  suspend fun deleteLastHeatReading(params: Map<String, String>) =
    postForm<GetSimpleResponse>("deleteCurrentHeatReading.php", params)

  suspend fun getLastHeatReading(params: Map<String, String>) =
    postForm<GetLastHeatReadingResponse>("getLastHeatReading.php", params)

  // --- Services & Payments ---
  suspend fun getFlatService(params: Map<String, String>) =
    postForm<GetServiceResponse>("getFlatServices.php", params)

  suspend fun getFlatPayment(params: Map<String, String>) =
    postForm<GetPaymentResponse>("getFlatPayments.php", params)

  suspend fun insertPayment(params: Map<String, String>) =
    postForm<InsertPaymentResponse>("newPaymentXpay.php", params)

  suspend fun sendNotificationToUser(params: Map<String, String>) =
    postForm<GetSimpleResponse>("sendNotificationToUser.php", params)
}
