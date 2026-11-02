package com.aura.music

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

// ================= THEME COLORS =================
val AuraBlack = Color(0xFF070708)
val AuraSurfaceDark = Color(0xFF121214)
val AuraCardDark = Color(0xFF1B1B1E)
val AuraCardBorder = Color(0xFF26262B)
val AuraNeon = Color(0xFF00FF66)
val AuraMutedGreen = Color(0xFF00B048)
val AuraTextPrimary = Color(0xFFFFFFFF)
val AuraTextSecondary = Color(0xFF9E9EA7)
val AuraTextMuted = Color(0xFF5E5E66)

// ================= MODELS =================
data class Song(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val mediaUri: String,
    val durationMs: Long = 0L,
    val isLocal: Boolean = false
)

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<String> = emptyList()
)

val Context.dataStore by preferencesDataStore(name = "aura_store")

// ================= PLAYER ENGINE =================
class AuraPlayerManager(context: Context) {
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main)

    val currentSong = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)

    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration.value = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })

        scope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                delay(400)
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        playlist = songs
        currentIndex = startIndex
        playSong(songs[currentIndex])
    }

    private fun playSong(song: Song) {
        currentSong.value = song
        exoPlayer.setMediaItem(MediaItem.fromUri(song.mediaUri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playSong(playlist[currentIndex])
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playSong(playlist[currentIndex])
    }

    fun seekTo(pos: Long) {
        exoPlayer.seekTo(pos)
        currentPosition.value = pos
    }

    fun release() {
        exoPlayer.release()
    }
}

// ================= VIEWMODEL =================
class AuraViewModel(application: Application) : AndroidViewModel(application) {
    val playerManager = AuraPlayerManager(application)
    private val gson = Gson()
    private val LIKED_KEY = stringPreferencesKey("aura_likes")
    private val PLAYLISTS_KEY = stringPreferencesKey("aura_playlists")
    private val LOCAL_KEY = stringPreferencesKey("aura_locals")

    val demoSongs = listOf(
        Song("1", "Midnight Odyssey", "Aura Sound Lab", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&q=80", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", 372000L),
        Song("2", "Cybernetic Dreams", "Kavinsky Wave", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", 423000L),
        Song("3", "Neon Velocity", "Lofi Synthetics", "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&q=80", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", 345000L),
        Song("4", "Electric Pulse", "Aura Collective", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", 301000L)
    )

    val searchQuery = MutableStateFlow("")
    val isPlayerExpanded = MutableStateFlow(false)

    val likedSongIds = application.dataStore.data.map { prefs ->
        val json = prefs[LIKED_KEY] ?: "[]"
        gson.fromJson<Set<String>>(json, object : TypeToken<Set<String>>() {}.type) ?: emptySet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val playlists = application.dataStore.data.map { prefs ->
        val json = prefs[PLAYLISTS_KEY] ?: "[]"
        gson.fromJson<List<Playlist>>(json, object : TypeToken<List<Playlist>>() {}.type) ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localSongs = application.dataStore.data.map { prefs ->
        val json = prefs[LOCAL_KEY] ?: "[]"
        gson.fromJson<List<Song>>(json, object : TypeToken<List<Song>>() {}.type) ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSongs = combine(localSongs) { locals ->
        demoSongs + locals[0]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), demoSongs)

    val filteredSongs = combine(allSongs, searchQuery) { songs, query ->
        if (query.isBlank()) songs else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), demoSongs)

    fun toggleLike(songId: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            app.dataStore.edit { prefs ->
                val current: MutableSet<String> = gson.fromJson(prefs[LIKED_KEY] ?: "[]", object : TypeToken<MutableSet<String>>() {}.type) ?: mutableSetOf()
                if (current.contains(songId)) current.remove(songId) else current.add(songId)
                prefs[LIKED_KEY] = gson.toJson(current)
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            app.dataStore.edit { prefs ->
                val list: MutableList<Playlist> = gson.fromJson(prefs[PLAYLISTS_KEY] ?: "[]", object : TypeToken<MutableList<Playlist>>() {}.type) ?: mutableListOf()
                list.add(0, Playlist(name = name))
                prefs[PLAYLISTS_KEY] = gson.toJson(list)
            }
        }
    }

    fun importLocalAudio(uri: Uri) {
        val app = getApplication<Application>()
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(app, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Local Track"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Device Audio"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            val song = Song(title = title, artist = artist, artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80", mediaUri = uri.toString(), durationMs = duration, isLocal = true)
            viewModelScope.launch {
                app.dataStore.edit { prefs ->
                    val list: MutableList<Song> = gson.fromJson(prefs[LOCAL_KEY] ?: "[]", object : TypeToken<MutableList<Song>>() {}.type) ?: mutableListOf()
                    list.add(0, song)
                    prefs[LOCAL_KEY] = gson.toJson(list)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

// ================= UI & SCREENS =================
enum class BottomTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.LibraryMusic)
}

class MainActivity : ComponentActivity() {
    private val viewModel: AuraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedTab by remember { mutableStateOf(BottomTab.HOME) }
            val allSongs by viewModel.allSongs.collectAsState()
            val filteredSongs by viewModel.filteredSongs.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val likedIds by viewModel.likedSongIds.collectAsState()
            val playlists by viewModel.playlists.collectAsState()
            val localSongs by viewModel.localSongs.collectAsState()
            val currentSong by viewModel.playerManager.currentSong.collectAsState()
            val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()

            Box(modifier = Modifier.fillMaxSize().background(AuraBlack)) {
                when (selectedTab) {
                    BottomTab.HOME -> HomeScreen(allSongs) { s, q -> viewModel.playerManager.playQueue(q, q.indexOf(s)) }
                    BottomTab.SEARCH -> SearchScreen(searchQuery, { viewModel.searchQuery.value = it }, filteredSongs) { s, q -> viewModel.playerManager.playQueue(q, q.indexOf(s)) }
                    BottomTab.LIBRARY -> LibraryScreen(
                        likedCount = likedIds.size,
                        playlists = playlists,
                        localSongs = localSongs,
                        onCreatePlaylist = { viewModel.createPlaylist(it) },
                        onImportAudio = { viewModel.importLocalAudio(it) },
                        onLikedSongsClick = {
                            val likes = allSongs.filter { likedIds.contains(it.id) }
                            if (likes.isNotEmpty()) viewModel.playerManager.playQueue(likes, 0)
                        },
                        onSongSelected = { s, q -> viewModel.playerManager.playQueue(q, q.indexOf(s)) }
                    )
                }

                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (currentSong != null) {
                        MiniPlayerView(viewModel.playerManager) { viewModel.isPlayerExpanded.value = true }
                    }
                    NavigationBar(containerColor = AuraBlack, tonalElevation = 0.dp, modifier = Modifier.height(64.dp)) {
                        BottomTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.title, tint = if (selectedTab == tab) AuraNeon else AuraTextSecondary) },
                                label = { Text(tab.title, color = if (selectedTab == tab) AuraNeon else AuraTextSecondary) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = AuraBlack)
                            )
                        }
                    }
                }

                NowPlayingModal(
                    expanded = isPlayerExpanded,
                    onDismiss = { viewModel.isPlayerExpanded.value = false },
                    song = currentSong,
                    playerManager = viewModel.playerManager,
                    isLiked = currentSong?.let { likedIds.contains(it.id) } ?: false,
                    onToggleLike = { id -> viewModel.toggleLike(id) }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(songs: List<Song>, onSongSelected: (Song, List<Song>) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)
    ) {
        item {
            Text("AURA", color = AuraNeon, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Sound in full spectrum", color = AuraTextSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Featured Releases", color = AuraTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(songs) { song ->
                    Column(modifier = Modifier.width(140.dp).clickable { onSongSelected(song, songs) }) {
                        AsyncImage(model = song.artworkUrl, contentDescription = null, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(song.title, color = AuraTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = AuraTextMuted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text("Quick Picks", color = AuraTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }
        items(songs) { song ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(AuraSurfaceDark).clickable { onSongSelected(song, songs) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(model = song.artworkUrl, contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = AuraTextPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(song.artist, color = AuraTextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AuraNeon)
            }
        }
    }
}

@Composable
fun SearchScreen(query: String, onQueryChange: (String) -> Unit, songs: List<Song>, onSongSelected: (Song, List<Song>) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Search", color = AuraTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = query, onValueChange = onQueryChange, placeholder = { Text("Artists, tracks", color = AuraTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AuraNeon) },
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = AuraCardDark, unfocusedContainerColor = AuraCardDark, focusedTextColor = AuraTextPrimary, unfocusedTextColor = AuraTextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs) { song ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AuraSurfaceDark).clickable { onSongSelected(song, songs) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = song.artworkUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(song.title, color = AuraTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(song.artist, color = AuraTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(likedCount: Int, playlists: List<Playlist>, localSongs: List<Song>, onCreatePlaylist: (String) -> Unit, onImportAudio: (Uri) -> Unit, onLikedSongsClick: () -> Unit, onSongSelected: (Song, List<Song>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var playlistTitle by remember { mutableStateOf("") }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportAudio(it) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Your Library", color = AuraTextPrimary, fontSize = 28.sp, fontWeight = 
