package com.folhetosmart.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.models.Product
import com.folhetosmart.data.models.SupermarketResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductViewModel(
    private val apiService: ApiService
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadProducts(supermarket: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = apiService.getLatestProducts(supermarket)
                if (response.isSuccessful) {
                    val json = response.body()
                    if (json != null) {
                        // Parse JSON
                        val gson = Gson()
                        val data = gson.fromJson(json, SupermarketResponse::class.java)
                        _products.value = data.produtos
                    } else {
                        _error.value = "Sem dados disponíveis"
                    }
                } else {
                    _error.value = "Erro ao carregar dados"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearProducts() {
        _products.value = emptyList()
    }
}
