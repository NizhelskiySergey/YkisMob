package com.ykis.mob.ui.navigation.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.ui.theme.YkisPAMTheme

@Composable
fun ApartmentList(
    modifier : Modifier = Modifier,
    currentAddressId:Int,
    apartmentList: List<ApartmentEntity>,
    onClick : (Int) ->Unit
) {
    LaunchedEffect(apartmentList) {
        Log.d("YkisLog", apartmentList.toString())
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        item{
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(
            items = apartmentList,
            key = {
                it.addressId
            }
        ){
                apartment->
            ApartmentListItem(
                apartment = apartment,
                onClick = onClick,
                currentAddressId = currentAddressId
            )
        }
        item{
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


@Preview
@Composable
private fun PreviewApartmentList() {

    YkisPAMTheme {
        ApartmentList(currentAddressId = 12,
            apartmentList = listOf(ApartmentEntity(addressId = 12 , address = "Новобілярська 28-1/25"), ApartmentEntity(addressId = 1 , address = "Хіміків 6/12"))) {

        }
    }
}
