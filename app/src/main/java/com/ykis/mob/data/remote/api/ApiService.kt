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
import retrofit2.Call
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST


interface ApiService {
    companion object {
        private const val SERVER_URL = "https://is.yuzhny.com/YkisMobileRest"
//        const val SERVER_URL = "http://10.0.2.2/YkisPAM/YkisMobileRest"
//        const val SERVER_URL = "http://192.168.0.107:8080/YkisMobileRest"

//        const val SERVER_URL = "http://192.168.0.177/YkisPAM/YkisMobileRest"

//        const val SERVER_URL = "http://192.168.88.156/MobYkis/YkisMobileRest"

        const val BASE_URL = "$SERVER_URL/rest_api/"
        const val GET_FLAT = "getFlatById.php"
        const val GET_SERVICE_FLAT = "getFlatServices.php"
        const val GET_FLAT_PAYMENT = "getFlatPayments.php"
        const val GET_MY_FLATS = "getApartmentsByUser.php"
        const val DELETE_FLAT = "deleteFlatByUser.php"
        const val UPDATE_BTI = "updateBti.php"
        const val GET_BLOCKS = "getBlocks.php"
        const val GET_STREETS = "getStreetsFromBlock.php"
        const val GET_HOUSES = "getHousesFromStreet.php"
        const val GET_FLATS = "getFlatsFromHouse.php"
        const val ADD_FLAT_BY_USER = "addMyFlatByUser.php"
        const val GET_FAMILY = "getFamilyFromFlat.php"
        const val GET_WATER_METER = "getWaterMeter.php"
        const val GET_HEAT_METER = "getHeatMeter.php"
        const val GET_WATER_READINGS = "getWaterReadings.php"
        const val GET_HEAT_READINGS = "getHeatReadings.php"
        const val ADD_NEW_WATER_READING = "addCurrentWaterReading.php"
        const val ADD_NEW_HEAT_READING = "addCurrentHeatReading.php"
        const val DELETE_CURRENT_WATER_READING = "deleteCurrentWaterReading.php"
        const val DELETE_CURRENT_HEAT_READING = "deleteCurrentHeatReading.php"
        const val GET_LAST_WATER_READING = "getLastWaterReading.php"
        const val GET_LAST_HEAT_READING = "getLastHeatReading.php"
        const val INSERT_PAYMENT = "newPaymentXpay.php"
        const val SEND_NOTIFICATION_TO_USER = "sendNotificationToUser.php"
        const val PARAM_ADDRESS_ID = "address_id"
        const val UID = "uid"
        const val YEAR = "year"
        const val STREET_ID = "street_id"
        const val HOUSE_ID = "house_id"
        const val ADDRESS_ID = "address_id"
        const val VODOMER_ID = "vodomer_id"
        const val TEPLOMER_ID = "teplomer_id"
        const val POK_ID = "pok_id"
        const val NEW_VALUE = "new_value"
        const val CURRENT_VALUE = "current_value"
        const val CODE = "kod"
        const val SERVICE = "service"
        const val TOTAL = "total"
        const val PHONE = "phone"
        const val EMAIL = "email"
        const val BLOCK_ID = "raion_id"
        const val KVARTPLATA = "kvartplata"
        const val RFOND = "rfond"
        const val TEPLO = "teplo"
        const val VODA = "voda"
        const val TBO = "tbo"
        const val RECIPIENT_TOKEN = "recipient_token"
        const val TITLE = "title"
        const val BODY = "body"
    }

    //appartment

    @FormUrlEncoded
    @POST(GET_MY_FLATS)
    fun getApartmentList(@FieldMap params: Map<String, String>): Call<GetApartmentsResponse>
//    fun getApartmentList(@Query("uid") uid:String): Call<GetApartmentsResponse>

    @FormUrlEncoded
    @POST(GET_FLAT)
    fun getApartment(@FieldMap params: Map<String, String>): Call<GetApartmentResponse>

    @FormUrlEncoded
    @POST(ADD_FLAT_BY_USER)
    fun addApartment(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>

    @FormUrlEncoded
    @POST(GET_FAMILY)
    fun getFamilyList(@FieldMap params: Map<String, String>): Call<GetFamilyResponse>

    @FormUrlEncoded
    @POST(DELETE_FLAT)
    fun deleteApartment(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>

    @FormUrlEncoded
    @POST(UPDATE_BTI)
    fun updateBti(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>

    @FormUrlEncoded
    @POST(GET_FLAT)
    fun getFlatById(@FieldMap params: Map<String, String>): Call<GetApartmentsResponse>

    @FormUrlEncoded
    @POST(GET_SERVICE_FLAT)
    fun getFlatService(@FieldMap params: Map<String, String>): Call<GetServiceResponse>

    @FormUrlEncoded
    @POST(GET_FLAT_PAYMENT)
    fun getFlatPayment(@FieldMap params: Map<String, String>): Call<GetPaymentResponse>

    @FormUrlEncoded
    @POST(GET_WATER_METER)
    fun getWaterMeterList(@FieldMap params: Map<String, String>): Call<GetWaterMeterResponse>

    @FormUrlEncoded
    @POST(GET_WATER_READINGS)
    fun getWaterReadings(@FieldMap params: Map<String, String>): Call<GetWaterReadingsResponse>

    @FormUrlEncoded
    @POST(ADD_NEW_WATER_READING)
    fun addWaterReading(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>

    @FormUrlEncoded
    @POST(DELETE_CURRENT_WATER_READING)
    fun deleteLastWaterReading(@FieldMap params: Map<String, String>): Call<BaseResponse>

    @FormUrlEncoded
    @POST(GET_HEAT_METER)
    fun getHeatMeterList(@FieldMap params: Map<String, String>): Call<GetHeatMeterResponse>

    @FormUrlEncoded
    @POST(GET_HEAT_READINGS)
    fun getHeatReadings(@FieldMap params: Map<String, String>): Call<GetHeatReadingResponse>

    @FormUrlEncoded
    @POST(ADD_NEW_HEAT_READING)
    fun addHeatReading(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>

    @FormUrlEncoded
    @POST(DELETE_CURRENT_HEAT_READING)
    fun deleteLastHeatReading(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>

    @FormUrlEncoded
    @POST(GET_LAST_WATER_READING)
    fun getLastWaterReading(@FieldMap params: Map<String, String>): Call<GetLastWaterReadingResponse>

    @FormUrlEncoded
    @POST(GET_LAST_HEAT_READING)
    fun getLastHeatReading(@FieldMap params: Map<String, String>): Call<GetLastHeatReadingResponse>

    @FormUrlEncoded
    @POST(INSERT_PAYMENT)
    fun insertPayment(@FieldMap params: Map<String, String>): Call<InsertPaymentResponse>

    @FormUrlEncoded
    @POST(SEND_NOTIFICATION_TO_USER)
    fun sendNotificationToUser(@FieldMap params: Map<String, String>): Call<GetSimpleResponse>
}
