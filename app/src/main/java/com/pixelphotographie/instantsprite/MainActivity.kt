package com.pixelphotographie.instantsprite // Garde ton package actuel !

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore by preferencesDataStore(name = "timestamps_prefs")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                ChronoApp()
            }
        }
    }
}

@Composable
fun ChronoApp() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // Contrôleur pour forcer le scroll de la liste
    val listState = rememberLazyListState()

    val dataKey = stringPreferencesKey("timestamps_with_comments")
    val fileUriKey = stringPreferencesKey("current_file_uri")
    val eventNameKey = stringPreferencesKey("current_event_name")

    val savedDataString by context.dataStore.data.map { it[dataKey] ?: "{}" }.collectAsState(initial = "{}")
    val currentFileUriString by context.dataStore.data.map { it[fileUriKey] ?: "" }.collectAsState(initial = "")
    val savedEventName by context.dataStore.data.map { it[eventNameKey] ?: "" }.collectAsState(initial = "")

    var eventNameInput by remember { mutableStateOf("") }

    LaunchedEffect(savedEventName) {
        if (savedEventName.isNotEmpty() && eventNameInput.isEmpty()) {
            eventNameInput = savedEventName
        }
    }

    val eventsMap = remember(savedDataString) {
        try {
            val json = JSONObject(savedDataString)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key -> map[key] = json.getString(key) }
            map
        } catch (e: Exception) { mutableMapOf() }
    }

    // Tri strict par ordre décroissant (le plus récent en premier)
    val sortedDates = remember(eventsMap) {
        eventsMap.keys.sortedDescending()
    }

    // Remonter automatiquement tout en haut dès que le nombre d'éléments augmente
    LaunchedEffect(sortedDates.size) {
        if (sortedDates.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    fun saveMapToStorage(updatedMap: Map<String, String>) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val json = JSONObject()
                updatedMap.forEach { (key, value) -> json.put(key, value) }
                prefs[dataKey] = json.toString()
            }
        }
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    BufferedWriter(OutputStreamWriter(os)).use { writer ->
                        writer.write("=== ÉVÉNEMENT : ${eventNameInput.uppercase()} ===\n")
                        writer.write("Créé le : ${SimpleDateFormat("dd/MM/yyyy à HH:mm:ss", Locale.FRANCE).format(Date())}\n")
                        writer.write("--------------------------------------------------\n\n")
                    }
                }

                scope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[fileUriKey] = uri.toString()
                        prefs[eventNameKey] = eventNameInput
                    }
                }
                Toast.makeText(context, "Fichier '${eventNameInput}.txt' créé !", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur lors de l'initialisation du fichier", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun appendTextToFile(uriString: String, textToAppend: String) {
        if (uriString.isEmpty()) return
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openOutputStream(uri, "wa")?.use { os ->
                BufferedWriter(OutputStreamWriter(os)).use { writer ->
                    writer.write(textToAppend)
                }
            }
        } catch (e: Exception) {
            scope.launch {
                Toast.makeText(context, "Erreur d'écriture automatique.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // --- ZONE CONFIGURATION ÉVÉNEMENT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Nom de l'Événement (Nom du .txt) :", color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = eventNameInput,
                        onValueChange = { eventNameInput = it },
                        placeholder = { Text("Ex: Concert, Chantier...", color = Color.DarkGray, fontSize = 15.sp) },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF222222),
                            unfocusedContainerColor = Color(0xFF222222),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (eventNameInput.isNotBlank()) {
                                createFileLauncher.launch("${eventNameInput.trim()}.txt")
                            } else {
                                Toast.makeText(context, "Entre un nom d'événement !", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text("Créer", fontSize = 14.sp)
                    }
                }

                if (currentFileUriString.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Actif : Enregistre en continu dans '${savedEventName}.txt'",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- GROS BOUTON CENTRAL ROUGE ---
        Button(
            onClick = {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)
                val currentDateTime = sdf.format(Date())

                val newMap = eventsMap.toMutableMap()
                newMap[currentDateTime] = ""
                saveMapToStorage(newMap)

                if (currentFileUriString.isNotEmpty()) {
                    val logBlock = "[DATE & HEURE] : $currentDateTime\n[COMMENTAIRE]  : (En attente de rédaction)\n--------------------------------------------------\n"
                    appendTextToFile(currentFileUriString, logBlock)
                } else {
                    Toast.makeText(context, "⚠️ Pense à créer l'événement pour activer la sauvegarde automatique .txt !", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.size(170.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text("CAPTURER", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "Captures de la session (${sortedDates.size}) :",
            color = Color.Gray,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- LA LISTE DES ENREGISTREMENTS (AVEC AUTO-SCROLL ET TRIS EFFECTIFS) ---
        LazyColumn(
            state = listState, // L'état lié pour permettre l'animation de défilement
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(sortedDates, key = { it }) { date ->
                var textState by remember(savedDataString) { mutableStateOf(eventsMap[date] ?: "") }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = date, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)

                            IconButton(
                                onClick = {
                                    val newMap = eventsMap.toMutableMap()
                                    newMap.remove(date)
                                    saveMapToStorage(newMap)

                                    if (currentFileUriString.isNotEmpty()) {
                                        appendTextToFile(currentFileUriString, "[INFO] : Élément du $date masqué de la vue.\n")
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(26.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextField(
                            value = textState,
                            onValueChange = { newText ->
                                textState = newText
                                val newMap = eventsMap.toMutableMap()
                                newMap[date] = newText
                                saveMapToStorage(newMap)
                            },
                            placeholder = { Text("Écris les détails ici...", color = Color.Gray, fontSize = 18.sp) },
                            textStyle = TextStyle(fontSize = 18.sp),
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF2C2C2C),
                                unfocusedContainerColor = Color(0xFF252525),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 5
                        )
                    }
                }
            }
        }
    }
}