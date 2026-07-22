package app.vercel.visionhubs.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vercel.visionhubs.model.Game
import app.vercel.visionhubs.util.ConnectivityObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = Firebase.database.reference
    private val connectivityObserver = ConnectivityObserver(application)
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    private val sharedPrefs = application.getSharedPreferences("nwc_settings", Context.MODE_PRIVATE)
    
    private val _rawGames = MutableStateFlow<List<Game>>(emptyList())
    val rawGames: StateFlow<List<Game>> = _rawGames.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow("Todas")
    val selectedSubCategory: StateFlow<String> = _selectedSubCategory.asStateFlow()

    private val _selectedThemeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 1))
    val selectedThemeMode: StateFlow<Int> = _selectedThemeMode.asStateFlow()

    private val _monetEnabled = MutableStateFlow(sharedPrefs.getBoolean("monet_enabled", true))
    val monetEnabled: StateFlow<Boolean> = _monetEnabled.asStateFlow()

    val games: StateFlow<List<Game>> = combine(_rawGames, _searchQuery, _selectedSubCategory) { rawList, query, subCat ->
        var filtered = rawList
        if (subCat != "Todas") {
            filtered = filtered.filter { it.category.equals(subCat, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.desc.contains(query, ignoreCase = true) 
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentSection = MutableStateFlow("games_android")
    val currentSection: StateFlow<String> = _currentSection.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private var activeListener: ValueEventListener? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _isOnline.value = online
                if (online) {
                    loadSection(_currentSection.value)
                } else {
                    _isLoading.value = false
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSubCategory(subCat: String) {
        _selectedSubCategory.value = subCat
    }

    fun setThemeMode(mode: Int) {
        _selectedThemeMode.value = mode
        sharedPrefs.edit().putInt("theme_mode", mode).apply()
    }

    fun setMonetEnabled(enabled: Boolean) {
        _monetEnabled.value = enabled
        sharedPrefs.edit().putBoolean("monet_enabled", enabled).apply()
    }

    fun handleAuthAction() {
        _isAdmin.value = !_isAdmin.value
    }

    fun loadSection(section: String) {
        _currentSection.value = section
        _searchQuery.value = ""
        _selectedSubCategory.value = "Todas"
        
        activeListener?.let { database.child(_currentSection.value).removeEventListener(it) }
        
        if (!_isOnline.value) {
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Game>()
                for (child in snapshot.children) {
                    val game = child.getValue(Game::class.java)?.copy(id = child.key ?: "")
                    if (game != null) list.add(game)
                }
                
                val sortedList = list.sortedWith(
                    compareByDescending<Game> { it.pinned }
                        .thenByDescending { it.createdAt }
                )
                
                _rawGames.value = sortedList
                _isLoading.value = false
            }

            override fun onCancelled(error: DatabaseError) {
                _isLoading.value = false
            }
        }
        
        activeListener = listener
        database.child(section).addValueEventListener(listener)
    }

    fun signInAdmin(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            onResult(false, "Preencha todos os campos.")
            return
        }
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        if (!email.trim().matches(emailPattern.toRegex())) {
            onResult(false, "E-mail inválido.")
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { _ -> onResult(true, null) }
            .addOnFailureListener { exception -> onResult(false, exception.message) }
    }

    fun saveGame(game: Game, onComplete: (Boolean) -> Unit) {
        val section = _currentSection.value
        val gameRef = if (game.id.isBlank()) {
            database.child(section).push()
        } else {
            database.child(section).child(game.id)
        }

        val gameData = mapOf(
            "name" to game.name,
            "desc" to game.desc,
            "category" to game.category,
            "banner" to game.banner,
            "pinned" to game.pinned,
            "createdAt" to game.createdAt,
            "linkObjects" to game.linkObjects.map { mapOf("label" to it.label, "url" to it.url) },
            "links" to game.linkObjects.map { it.url }
        )

        gameRef.setValue(gameData)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteGame(id: String, onComplete: (Boolean) -> Unit) {
        database.child(_currentSection.value).child(id).removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteAllGames(onComplete: (Boolean) -> Unit) {
        database.child(_currentSection.value).removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}
