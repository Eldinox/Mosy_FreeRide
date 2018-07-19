/*FreeRide App MainActivity
* Version: 3.0
* Author: Waldemar Goßmann, Max Hammer
* First Published: 12.07.2018
* Last Update: 12.07.2018
* Created in: Android Studio 3.1.2
* */

package com.eldinox.freerideprototype3
/*Importe*/
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.System.out
import java.text.DecimalFormat
import java.util.*

//Initialisierung der Variablen
var AppContext: Context? = null                                 //Context der App
val topic = "mosy/freeride"                                     //Definiert das Topic fuer den MQTT
var mode = 0                                                    //Gibt an in welchem Modus man sich befindet (Menue = 0, RaceMode = 1, DiscoveryMode = 2)
val delay: Long = 200                                           //Gibt die Sendefrequenz an
var speed = "50"                                                //Geschwindigkeit des Autos (50 = Stillstand, 0 = volle Geschwindigkeit rueckwaerts, 100 = volle Geschwindigkeit vorwaerts)
var directionRace = "50"                                        //Richtung beim RaceMode (50 = Geradeaus, 0 = voll links, 100 = voll rechts)
var directionDisc = "50"                                        //Richtung beim DiscoveryMode (50 = Geradeaus, 0 = voll links, 100 = voll rechts)
var xTurnVal = 0.0f                                             //Gyrodaten der X-Achse
var yTurnVal = 0.0f                                             //Gyrodaten der Y-Achse
var zTurnVal = 0.0f                                             //Gyrodaten der Z-Achse
var cameraHo = 255.0f                                           //Horizontale Ausrichtung der Kamera
var cameraVe = 23.0f                                            //Vertikale Ausrichtung der Kamera
var highscorelistData = "no data"                                   //Initialinhalt der Bestenliste
var raceRunning = false                                             //Gibt an, ob gerade ein Rennen laeuft (true = rennen laeuft)
var seconds = 0                                                 //Rennzeit in Sekunden
var minutes = 0                                                 //Rennzeit in Minuten
var checkSec = 0                                                //Checkpointzeit in Sekunden
var checkMin = 0                                                //Checkpointzeit in Minuten
val formatter = DecimalFormat("00")                      //Formatter fuer das richtige Textformat beim Anzeigen der Sekunden als "00"
var playerName = ""                                             //Name des Spielers
val colorGray: Int = Color.rgb(140, 140, 140)    //Benutzerdefinierte Farbe Grau
val colorRed: Int = Color.rgb(255, 128, 128)     //Benutzerdefinierte Farbe Rot
val colorBlue: Int = Color.rgb(128, 170, 255)    //Benutzerdefinierte Farbe Blau

class MainActivity : AppCompatActivity(), SensorEventListener, MqttCallback
{
    lateinit var sensorManager: SensorManager                   //Manager fuer die Gyrodaten
    lateinit var usernameInput: EditText                        //Textfeld fuer die Eingabe des Namens
    var mywebview: WebView? = null                              //Das Fenster in dem der Stream angezeigt wird
    var client: MqttAndroidClient? = null                       //Initialisierung des client fuer den MQTT-Service
    var uri = "tcp://broker.hivemq.com:1883"                    //MQTT Broker Adresse
    lateinit var topiccallbacks: HashMap<String, (Boolean) -> Unit>


    override fun onAccuracyChanged(p0: Sensor?, p1: Int)        //ungenutze Gyro-Function
    {
        TODO("not implemented")
    }
    override fun onSensorChanged(event: SensorEvent?)           //Aktivierung bei Gyro-Daten Aenderung
    {
        xTurnVal = event!!.values[0]
        yTurnVal = event!!.values[1]
        zTurnVal = event!!.values[2]

        //Mapping von Gyro-Daten auf Richtungswerte
        if (mode == 1) {
            if (yTurnVal < -1.5)
            {
                directionRace = ((50 + (yTurnVal * 6)).toInt()).toString()
                if (directionRace.toInt() < 0) directionRace = "0"
            }
            else if (yTurnVal > 1.5)
            {
                directionRace = ((50 + (yTurnVal * 6)).toInt()).toString()
                if (directionRace.toInt() > 100) directionRace = "100"
            }
            else directionRace = "50"
        } else if (mode == 2) {
            if (yTurnVal < -1.5) {
                cameraHo = cameraHo + (yTurnVal * 0.28f)
                if (cameraHo < 127.0f) cameraHo = 127.0f
            } else if (yTurnVal > 1.5) {
                cameraHo = cameraHo + (yTurnVal * 0.28f)
                if (cameraHo > 382.0f) cameraHo = 382.0f
            }
        }

        //Anzeige von Gyro-Daten zum Debuggen
        /*gyroData.text = "x ${event!!.values[0]}\n\n" +
                "y ${event.values[1]}\n\n" +
                "z ${event.values[2]}\n\n"*/
    }

    //Verbindung zum MQTT Broker
    fun connect(url: String, port: Long, ConnectionListener: IMqttActionListener?)
    {
        val clientId = MqttClient.generateClientId()

        topiccallbacks = HashMap()

        client = MqttAndroidClient(AppContext, uri, clientId)
        client?.registerResources(AppContext)
        val options = MqttConnectOptions()
        client?.connect(options, null, ConnectionListener)

        client?.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("MqttCallback", topic)
                Log.d("MqttCallback", message.toString())
                if (topiccallbacks.containsKey(topic)) {
                    topiccallbacks.get(topic)?.invoke(message.toString() == "1")
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e("MqttCallback", "Connection lost!", cause)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d("MqttCallback", "deliveryComplete: \n${token}")
            }
        })
    }

    //Die Publish-Funktion sendet die Befehle an den MQTT Broker
    fun publish(topic: String, value: String)
    {
        client?.publish(topic, value.toByteArray(), 0, false)
    }

    //Ueberschreibung/Deaktivierung des "Back-Buttons" des Smartphones -> verhindern des Schließens der App
    override fun onBackPressed()
    {

    }

    //OnCreate wird beim Starten der App ausgefuehrt
    override fun onCreate(savedInstanceState: Bundle?) {
        //Uebergeben des Gyro-Sensors an den Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppContext = this.applicationContext

        //Verhindert automatisches Abschalten des Bildschirms
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        var payload = "Connected" //Nachrichten String

////INITIALISIERUNG////
        //Buttons
        val mButton1 = findViewById<Button>(R.id.menubutton1)                    //Menuebutton1 zum Starten des Discoverymode
        val mButton2 = findViewById<Button>(R.id.menubutton2)                    //Menuebutton2 zum Starten des Racemode
        val mButton3 = findViewById<Button>(R.id.menubutton3)                    //Menuebutton3 zum Oeffnen der Screenshot Sammlung
        val mButton4 = findViewById<Button>(R.id.menubutton4)                    //Menuebutton4 zum Oeffnen der Bestenliste
        val bButton = findViewById<Button>(R.id.backButton)                      //Backbutton um ins Hauptmenue zurueckzukehren
        val nImageButton = findViewById<Button>(R.id.nImage)                     //Next Image Button um in der Screenshot Sammlung zum naechsten Bild zu wechseln
        val pImageButton = findViewById<Button>(R.id.pImage)                     //Previous Image Button um in der Screenshot Sammlung zum letzten Bild zu wechseln
        val tiltUpButton = findViewById<Button>(R.id.tiltUp)                     //Button zum Hochbewegen der Kamera im Discoverymode
        val tiltDownButton = findViewById<Button>(R.id.tiltDown)                 //Button zum Runterbewegen der Kamera im Discoverymode
        val refreshStreamButton = findViewById<Button>(R.id.refreshStream)       //Button um den Stream neu zu laden, sollte dieser abstuerzen
        val screenshotButton = findViewById<Button>(R.id.screenshotButton)       //Button zum erstellen von Screenshots
        val newRaceButton = findViewById<Button>(R.id.newRace)                   //Button zum Zuruecksetzen der Bestenliste um einen neuen Kurs aufzubauen

        //Seekbars
        val speedBar = findViewById<SeekBar>(R.id.speedBar)                     //Schieberegler zum Regeln der Geschwindigkeit und Bewegungsrichtung (Vorwaerts oder Rueckwaerts)
        val directionBar = findViewById<SeekBar>(R.id.directionBar)             //Schieberegler zum Regeln der Fahrtrichtung (Links oder Rechts)

        //Textfeld
        val modeText = findViewById<TextView>(R.id.modeText)                   //Textfeld, das kurz anzeigt in welchem Modus man sich befindet. Im Racemode dann die Rennzeit
        val scoreboard = findViewById<TextView>(R.id.scoreBoard)               //Textfeld, das die Bestenliste anzeigt
        usernameInput = findViewById(R.id.nameField) as EditText                        //Textfeld zur Names-Eingabe

        //Views
        val wView = findViewById<WebView>(R.id.webView)                        //Webview
        val gallery = findViewById<ImageView>(R.id.imageView)                 //ImageView

        //Varibalen
        var imageFound = false                                                          //Gibt an, ob schon ein Bild im Speicher vorhanden ist
        var imageNumber = 0                                                             //Zur Nummerierung der gespeicherten Bilder
        var imagePath: String = "mnt/sdcard/image" + imageNumber.toString() + ".png"     //Pfad zum Speicherort der Bilder
        var loadImageFile = File(imagePath)                                             //Das Bild als File
        var myBitmap : Bitmap                                                           //Das Bild als Bitmap das angezeigt werden kann

        //Initialaufruf des Hauptmenues (View.VISIBLE aktiviert Elemente, View.GONE deaktiviert sie)
        /*
        Deaktiviert: Alles was nicht im Hauptmenue zu sehen sein soll.
        Faerbt: die Buttons (Die Buttons die mit dem Racemode zu tun haben bekommen hier eine rote Farbe, die des Discoverymodes eine blaue).
         */
        modeText.visibility = View.GONE
        speedBar.visibility = View.GONE
        directionBar.visibility = View.GONE
        wView.visibility = View.GONE
        scoreboard.visibility = View.GONE
        bButton.visibility = View.GONE
        refreshStreamButton.visibility = View.GONE
        screenshotButton.visibility = View.GONE
        newRaceButton.visibility = View.GONE
        scoreboard.text = highscorelistData
        usernameInput.visibility = View.GONE
        gallery.visibility = View.GONE
        nImageButton.visibility = View.GONE
        pImageButton.visibility = View.GONE
        tiltUpButton.visibility = View.GONE
        tiltDownButton.visibility = View.GONE
        bButton.setBackgroundColor(colorGray)
        nImageButton.setBackgroundColor(colorGray)
        pImageButton.setBackgroundColor(colorGray)
        tiltUpButton.setBackgroundColor(colorBlue)
        tiltDownButton.setBackgroundColor(colorBlue)
        mButton1.setBackgroundColor(colorBlue)
        mButton2.setBackgroundColor(colorRed)
        mButton3.setBackgroundColor(colorBlue)
        mButton4.setBackgroundColor(colorRed)
////INITIALISIERUNG ENDE////

////BUTTON FUNKTIONEN////
        //Button Discovery Mode
        /*
        Deativiert: Menuebuttons
        Aktiviert: das Textfeld zur Anzeige der Modus, Geschwindigkeitsregler, Richtungsregler, Buttons zum tilten der Kamera, den Backbutton, den Button
            zum Neuladen des Streams, den Screenshotbutton und die Webview zur Uebertragung des Streams.
        Faerbt: Backbutton und Refreshbutton blau, entsprechend des Discoverymodes.
        Setzt: das Textfeld auf "Discovery Mode", den Modus auf den Wert 2 fuer Discoverymode und den vertikalen Wert der Kameraausrichtung auf 23 (Ausganswert).
        */
        mButton1.setOnClickListener()
        {
            mButton1.visibility = View.GONE
            mButton2.visibility = View.GONE
            mButton3.visibility = View.GONE
            mButton4.visibility = View.GONE
            modeText.visibility = View.VISIBLE
            speedBar.visibility = View.VISIBLE
            directionBar.visibility = View.VISIBLE
            tiltUpButton.visibility = View.VISIBLE
            tiltDownButton.visibility = View.VISIBLE
            wView.visibility = View.VISIBLE
            bButton.visibility = View.VISIBLE
            refreshStreamButton.visibility = View.VISIBLE
            screenshotButton.visibility = View.VISIBLE
            modeText.text = "Discovery Mode"
            mode = 2
            cameraVe = 23.0f
            bButton.setBackgroundColor(colorBlue)
            refreshStreamButton.setBackgroundColor(colorBlue)
        }

        //Button Race Mode
        /*
        Deativiert: Menuebuttons.
        Aktiviert: das Textfeld zur Anzeige der Modus, Geschwindigkeitsregler, den Backbutton, den Button zum Neuladen des Streams und die Webview zur Uebertragung des Streams.
        Faerbt: Backbutton und Refreshbutton rot, entsprechend des Racemodes.
        Called: die setName()-Funktion zum festlegen des Spielernamens.
        Setzt: das Textfeld auf "Race Mode" und den Modus auf den Wert 1 fuer Racemodemode.
        */
        mButton2.setOnClickListener()
        {
            mButton1.visibility = View.GONE
            mButton2.visibility = View.GONE
            mButton3.visibility = View.GONE
            mButton4.visibility = View.GONE
            modeText.visibility = View.VISIBLE
            speedBar.visibility = View.VISIBLE
            wView.visibility = View.VISIBLE
            bButton.visibility = View.VISIBLE
            refreshStreamButton.visibility = View.VISIBLE
            modeText.text = "Race Mode"
            mode = 1
            bButton.setBackgroundColor(colorRed)
            refreshStreamButton.setBackgroundColor(colorRed)
            setName()
        }

        //Button Screenshot-Galerie
        /*
        Deativiert: Menuebuttons.
        Aktiviert: den Backbutton, die Bildergalerie und die Buttons zum wechseln des Bildes.
        Faerbt: Backbutton blau, entsprechend des Discoverymodes.
        Setzt: die Imageview der Gallery auf das erste Bild im Speicher.
        */
        mButton3.setOnClickListener()
        {
            mButton1.visibility = View.GONE
            mButton2.visibility = View.GONE
            mButton3.visibility = View.GONE
            mButton4.visibility = View.GONE
            bButton.visibility = View.VISIBLE
            gallery.visibility = View.VISIBLE
            nImageButton.visibility = View.VISIBLE
            pImageButton.visibility = View.VISIBLE
            bButton.setBackgroundColor(colorBlue)

            //Erstes Bild anzeigen in der Galerie
            imageNumber = 0
            imagePath = "mnt/sdcard/image" + imageNumber.toString() + ".png"

            while (imageFound == false) {
                imagePath = "mnt/sdcard/image" + imageNumber.toString() + ".png"

                if (loadImageFile.exists())
                {
                    myBitmap = BitmapFactory.decodeFile(loadImageFile.getAbsolutePath())
                    gallery.setImageBitmap(myBitmap)
                    imageFound = true
                    break
                }
                else {
                    imageNumber++
                }
            }
        }

        //Button Highscore-Liste
        /*
        Deativiert: Menuebuttons.
        Aktiviert: den Backbutton, die Bestenliste und den Button zum loeschen der Bestenliste.
        Faerbt: Backbutton und Button zum loeschen der Bestenliste rot, entsprechend des Racemodes.
        Called: die readHighscores()-Funktion um die Variable highscorelistData mit dem Text aus der gespeicherten Datei zu fuellen. Der Context der App wird hier als Parameter uebergeben.
        Setzt: den Inhalt der Bestenliste auf die in der Variable highscorelistData gespeicherten Werte und formatiert diese richtig.
        */
        mButton4.setOnClickListener()
        {
            mButton1.visibility = View.GONE
            mButton2.visibility = View.GONE
            mButton3.visibility = View.GONE
            mButton4.visibility = View.GONE
            bButton.visibility = View.VISIBLE
            scoreboard.visibility = View.VISIBLE
            newRaceButton.visibility = View.VISIBLE
            bButton.setBackgroundColor(colorRed)
            newRaceButton.setBackgroundColor(colorRed)
            readHighscores(applicationContext)

            //Laden der Highscore-Liste
            val separated = highscorelistData.split(" - ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            scoreboard.text = separated[0] + " - " + separated[1] + " - " + separated[2] +"\n" +
                    separated[3] + " - " + separated[4] + " - " + separated[5] +"\n" +
                    separated[6] + " - " + separated[7] + " - " + separated[8] +"\n" +
                    separated[9] + " - " + separated[10] + " - " + separated[11] +"\n" +
                    separated[12] + " - " + separated[13] + " - " + separated[14]
        }

        //Back Button
        /*
        Deativiert: alle Buttons und Regler, die im Menue nicht zu sehen sein sollen.
        Aktiviert: die 4 Menuebuttons.
        Setzt: den Modus wieder auf 0 fuer Hauptmenue, die Zeit des Rennens wieder auf 0 und beendet das Rennen mit raceRunning = false.
        */
        bButton.setOnClickListener()
        {
            modeText.visibility = View.GONE
            speedBar.visibility = View.GONE
            directionBar.visibility = View.GONE
            wView.visibility = View.GONE
            bButton.visibility = View.GONE
            scoreboard.visibility = View.GONE
            refreshStreamButton.visibility = View.GONE
            newRaceButton.visibility = View.GONE
            screenshotButton.visibility = View.GONE
            usernameInput.visibility = View.GONE
            gallery.visibility = View.GONE
            tiltUpButton.visibility = View.GONE
            tiltDownButton.visibility = View.GONE
            nImageButton.visibility = View.GONE
            pImageButton.visibility = View.GONE
            mButton1.visibility = View.VISIBLE
            mButton2.visibility = View.VISIBLE
            mButton3.visibility = View.VISIBLE
            mButton4.visibility = View.VISIBLE
            mode = 0
            //Zuruecksetzen der Race-Time
            raceRunning = false
            minutes = 0
            seconds = 0
        }

        //Button Kamera nach unten tilten in 3er-Schritten ohne dabei den Ausganswert 23 zu ueberschreiten.
        tiltDownButton.setOnClickListener()
        {
            if(cameraVe < 20)
            {
                cameraVe += 3
            }
            else cameraVe = 23.0f
        }

        //Button Kamera nach oben tilten in 3er-Schritten ohne dabei den Wert fuer die hoechste Position 0 zu unterschreiten.
        tiltUpButton.setOnClickListener()
        {
            if(cameraVe > 3)
            {
                cameraVe -= 3
            }
            else cameraVe = 0.0f
        }

        //Button naechstes Bild
        //Zaehlt die Variable imageNumber einen hoch und ueberprueft ob ein Bild mit dieser Nummer vorhanden ist und zeigt es an. Wenn nicht zaehlt er wieder runter.
        nImageButton.setOnClickListener()
        {
            imageNumber++
            imagePath = "mnt/sdcard/image" + imageNumber.toString() + ".png"
            loadImageFile = File(imagePath)
            if (loadImageFile.exists()) {
                myBitmap = BitmapFactory.decodeFile(loadImageFile.getAbsolutePath())
                gallery.setImageBitmap(myBitmap)
            }
            else{
                imageNumber--
            }
        }
        //Button Vorherriges Bild
        //Zaehlt die Variable imageNumber einen runter wenn diese mindestens 1 ist und zeigt das jeweilige Bild an.
        pImageButton.setOnClickListener()
        {
            if (imageNumber > 0){
                imageNumber--
                imagePath = "mnt/sdcard/image" + imageNumber.toString() + ".png"
                loadImageFile = File(imagePath)
                myBitmap = BitmapFactory.decodeFile(loadImageFile.getAbsolutePath())
                gallery.setImageBitmap(myBitmap)
            }
        }

        //Button Screenshot aufnehmen
        //Called: die captureScreen()-Funktion.
        screenshotButton.setOnClickListener()
        {
            captureScreen()
        }

        //Button Highscore-Liste reseten
        /*
        Setzt: die Variable highscorelistData auf den Platzhalter-Ausganswert.
        Called: die writeHighscores()-Funktion um den Inhalt der Variable highscorelistData in der gespeicherten Datei zu sichern und uebergibt dabei den Context der App als Parameter.
        Setzt: die Bestenliste sofort auf die aktualisierten Werte.
         */
        newRaceButton.setOnClickListener()
        {
            highscorelistData = "Spieler1 - 29:55 - 59:55 - Spieler2 - 29:56 - 59:56 - Spieler3 - 29:57 - 59:57 - Spieler4 - 29:58 - 59:58 - Spieler5 - 25:59 - 29:59"
            writeHighscores(applicationContext)
            val separated = highscorelistData.split(" - ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            scoreboard.text = separated[0] + " - " + separated[1] + " - " + separated[2] +"\n" +
                    separated[3] + " - " + separated[4] + " - " + separated[5] +"\n" +
                    separated[6] + " - " + separated[7] + " - " + separated[8] +"\n" +
                    separated[9] + " - " + separated[10] + " - " + separated[11] +"\n" +
                    separated[12] + " - " + separated[13] + " - " + separated[14]
        }

        //Button Stream reloaden
        //Uebergibt den URL unseres Livestreams als Parameter an die loadUrl()-Funktion unserer WebView um den Stream neu zu laden.
        refreshStreamButton.setOnClickListener()
        {
            mywebview!!.loadUrl("http://192.168.0.180:8081")
        }
////BUTTON FUNKTIONEN ENDE////

        //WebView
        //Konfiguration der Webview um den Livestream anzuzeigen.
        mywebview = findViewById<WebView>(R.id.webView)
        mywebview!!.webViewClient = object : WebViewClient()
        {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean
            {
                view?.loadUrl(url)
                return true
            }
        }
        //Laden der MJEG-Streams
        mywebview!!.loadUrl("http://192.168.0.180:8081")

////TIMER UND HANDLER////
        //Erstellen des Timers, der im Racemode die Rennzeit hochzaehlt und jede Sekunde die Textview mit der Zeit im richtigen Format aktualisiert.
        val T = Timer()
        T.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    if (raceRunning) seconds++
                    if (seconds == 60) {
                        minutes++
                        seconds = 0
                    }
                    modeText.text = minutes.toString() + ":" + (formatter.format(seconds)).toString()
                }
            }
        }, 1000, 1000)

        //Erstellt den Handler, der in einem frei waehlbaren Intervall eine Aktion durchfuehrt.
        /*
        Es wird zuerst sichergestellt, dass der Richtungswert im Racemode zwischen 0 fuer ganz links und 100 fuer ganz rechts liegt.
        Anschließend wird je nach Modus der payload aus den Richtungs-, Geschwindigkeits- und ggf Kamerawerten bestimmt.
        Abschließend wird die publish()-Funktion mit dem topic an das gesendet werden soll und dem Payload als Parametern aufgerufen um die Nachricht zu senden.
        Die Variable delay stellt hierbei den Zeitraum zwischen den Intervallen dar.
        */
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (directionRace.toInt() >= 100) {
                    directionRace = "100"
                }
                if (directionRace.toInt() <= 0) {
                    directionRace = "0"
                }
                if (mode == 1) payload = speed + " " + directionRace + " RR" + " RR"
                else if (mode == 2) {
                    payload = speed + " " + directionDisc + " " + cameraHo.toInt().toString() + " " + cameraVe.toInt().toString()
                }
                if (mode != 0) {
                    publish(topic, payload)
                }
                handler.postDelayed(this, delay)
            }
        }, delay)
////TIMER UND HANDLER ENDE////

////CHANGELISTENER////
        //Changelistener des Geschwindigkeitsreglers
        speedBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener
        {
            override fun onStopTrackingTouch(seekBar: SeekBar)                                      //Wenn kein Touch-Feedback mehr vernommen wird, soll der Regler wieder in die Mitte springen
            {
                // TODO Auto-generated method stub
                speedBar.progress = 50
            }
            override fun onStartTrackingTouch(seekBar: SeekBar)
            {
                // TODO Auto-generated method stub
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean)      //Die Variable speed soll bei jeder Veraenderung aktualisiert werden
            {
                // TODO Auto-generated method stub
                speed = speedBar.progress.toString()
            }
        })
        //Changelistener des Richtungsreglers im Discoverymode
        directionBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener
        {
            override fun onStopTrackingTouch(seekBar: SeekBar)                                      //Wenn kein Touch-Feedback mehr vernommen wird, soll der Regler wieder in die Mitte springen
            {
                // TODO Auto-generated method stub
                directionBar.progress = 50
            }
            override fun onStartTrackingTouch(seekBar: SeekBar)
            {
                // TODO Auto-generated method stub
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean)      //Die Variable directionDisc soll bei jeder Veraenderung aktualisiert werden
            {
                // TODO Auto-generated method stub
                directionDisc = directionBar.progress.toString()
            }
        })
////CHANGELISTENER ENDE////

        //Aufruf der connect()-Funktion mit dem URL zum Broker, dem gewaehlten Port und einem Listener als Parameter
        connect("broker.hivemq.com", 1883, null)

        //Herstellen der Verbindung mit dem gesetzten Clienten
        try {
            val token = client?.connect()
            token?.actionCallback = object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    System.out.println("connected!")
                    try
                    {
                        client?.subscribe(topic, 2)
                    }
                    catch (e: MqttException)
                    {
                        e.printStackTrace()
                    }
                }
                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    System.out.println("failed!")
                }
            }
        } catch (e: MqttException) {
            e.printStackTrace()
        }
        client?.setCallback(this)
    }

////SONSTIGE FUNKTIONEN////
    //Funktion Screenshot aufnehmen
    /*
    imageCounter setzt die Nummer im Namen des Bildes auf den naechsten freien Platz.
    newName gibt an, ob bereits ein Bild unter diesem Namen vorhanden ist.
    mPath gibt den Speicher-Pfad und Datei-Namen im Stil von Image + Nummer (Image1.png, Image2.png, etc) an.
     */
    private fun captureScreen()
    {
        var imageCounter = 0
        var newName = false
        var mPath: String = "mnt/sdcard/image" + imageCounter.toString() + ".png"

        // Erstellen von Bitmap aus Capture
        val bitmap: Bitmap
        val v1: View = webView.getRootView()
        v1.setDrawingCacheEnabled(true)
        bitmap = Bitmap.createBitmap(v1.getDrawingCache())
        v1.setDrawingCacheEnabled(false)

        var fout:OutputStream
        var imageFile = File(mPath)

        //Speichern des Scrennshots auf dem naechsten freien Datei-Namen
        /*
        waehrend bereits ein Bild mit dem angegebenen Dateinamen existiert, bleibt newName false, imageCounter wird eins hochgezaehlt und der neue Pfad wird gesetzt.
        wird ein Dateiname gefunden der noch nicht existiert (zb. image3.png), wird newName auf true gesetzt um die while-Schleife zu beenden und das Bild wird gespeichert.
         */
        while (newName == false)
        {
            if (imageFile.exists())
            {
                imageCounter ++
                mPath = "mnt/sdcard/image" + imageCounter.toString() + ".png"
                imageFile = File(mPath)
            }
            else {
                try
                {
                    fout = FileOutputStream(imageFile)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fout)
                    fout.flush()
                    fout.close()
                    newName = true

                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    //Wird die Verbindung unterbrochen, wird die Ursache dafuer in der Konsole ausgegeben.
    override fun connectionLost(cause: Throwable?)
    {
        out.println("Connection was lost: " + cause)
    }
    //Ungenutzte Override-Funktion fuer durchgefuehrte Uebertragungen
    override fun deliveryComplete(token: IMqttDeliveryToken?)
    {
    }
    //Waehrend man im Racemode ist (mode = 1), reagiert die App auf drei verschiedene Nachrichten des Brokers, die die drei zu durchfahrenden Tore repreaesentieren.
    override fun messageArrived(topic: String?, message: MqttMessage?)
    {
        if(mode == 1)
        {
            //Wird ein "b" empfangen, wurde das blaue Tor durchfahren und das Rennen gestartet. Eine Start-Meldung wird auf dem Display angezeigt und die Variable raceRunning wird auf true gesetzt.
            if(message.toString() == "b")
            {
                Toast.makeText(applicationContext, "START!!", Toast.LENGTH_SHORT).show()
                raceRunning = true
            }
            //Wird ein "r" empfangen, wurde das rote Tor durchfahren und damit der Checkpoint passiert.
            //Die Rennzeit wird als Zwischenzeit gespeichert und die Differenz zum Ranglistenersten wird auf dem Display ausgegeben.
            else if(message.toString() == "r")
            {
                val separated = highscorelistData.split(" - ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val raceTime = 60 * minutes + seconds
                checkSec = seconds
                checkMin = minutes
                val separated1 = separated[1].trim().split(":")
                val combTime = 60 * Integer.parseInt(separated1[0]) + Integer.parseInt(separated1[1])
                val dif = raceTime - combTime
                if(dif >= 0)Toast.makeText(applicationContext,"+" + dif.toString() + " seconds", Toast.LENGTH_SHORT).show()
                else Toast.makeText(applicationContext, dif.toString() + " seconds", Toast.LENGTH_SHORT).show()
            }
            //Wird ein "g" empfangen, wurde das gruene Tor durchfahren und das Rennen beendet.
            //Die finale Rennzeit wird auf dem Display ausgegeben, die compairTime()-Funktion wird aufgerufen um zu ermitteln ob ein neuer Rekord vorliegt und die Variable raceRunning wird auf false gesetzt um die Zeit anzuhalten.
            else if(message.toString() == "g")
            {
                Toast.makeText(applicationContext, "Race Time: " + minutes.toString() + ":" + (formatter.format(seconds)).toString(), Toast.LENGTH_SHORT).show()
                if (raceRunning){
                    compairTime()
                }
                raceRunning = false
            }
        }
    }

    //Funktion Highscores lesen
    /*
    Der Pfad wird als Highscore/highscore.txt im Speicher des Geraets festgelegt.
    Sollte diese Datei bereits existieren wird ihr Inhalt in der Variable highscorelistData gespeichert.
    Falls nicht wird ein Standardwert gespeichert und die writeHighscore()-Funktion mit dem Context der App als Parameter aufgerufen um die Datei zu erstellen.
    */
    private fun readHighscores(context: Context)
    {
        val path = context.getExternalFilesDir(null)
        val highscoreDiretory = File(path, "Highscore")
        highscoreDiretory.mkdir()

        val file = File(highscoreDiretory, "highscore.txt")

        if(file.exists())
        {
            highscorelistData = FileInputStream(file).bufferedReader().use{
                it.readText()
            }
        }
        else
        {
            highscorelistData = "Spieler1 - 29:55 - 59:55 - Spieler2 - 29:56 - 59:56 - Spieler3 - 29:57 - 59:57 - Spieler4 - 29:58 - 59:58 - Spieler5 - 25:59 - 29:59"
            writeHighscores(applicationContext)
        }
    }
    //Funktion Highscores ueberschreiben
    //Der Pfad wird als Highscore/highscore.txt im Speicher des Geraets festgelegt und der aktuelle Inhalt der Variable highscorelistData wird in der Datei gespeichert.
    private fun writeHighscores(context: Context)
    {
        val path = context.getExternalFilesDir(null)
        val highscoreDiretory = File(path, "Highscore")
        highscoreDiretory.mkdir()

        val file = File(highscoreDiretory, "highscore.txt")
        FileOutputStream(file).use {
            file.appendText(highscorelistData)
        }
    }

    //Funktion Rennzeiten vergleichen
    /*
    Nachdem die readHighscores()-Funktion aufgerufen wird um die Variable highscorelistData zu aktualisieren, werden die gespeicherten Zeiten der Bestenliste mit der split()-Funktion geteilt.
    Nach der Verrechnung der Minuten und Sekunden erhaelt man dadurch fuer jeden Rekordhalter die Renn- und Checkpointzeit in Sekunden.
    Diese wird mit den Zeiten des aktuellen Fahrers verglichen um zu ermitteln, ob ein neuer Rekord vorliegt.
    */
    private fun compairTime()
    {
        readHighscores(applicationContext)

        val separated = highscorelistData.split(" - ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val separated1 = separated[2].trim().split(":")
        val separated2 = separated[5].trim().split(":")
        val separated3 = separated[8].trim().split(":")
        val separated4 = separated[11].trim().split(":")
        val separated5 = separated[14].trim().split(":")

        val combTime1 = 60 * Integer.parseInt(separated1[0]) + Integer.parseInt(separated1[1])
        val combTime2 = 60 * Integer.parseInt(separated2[0]) + Integer.parseInt(separated2[1])
        val combTime3 = 60 * Integer.parseInt(separated3[0]) + Integer.parseInt(separated3[1])
        val combTime4 = 60 * Integer.parseInt(separated4[0]) + Integer.parseInt(separated4[1])
        val combTime5 = 60 * Integer.parseInt(separated5[0]) + Integer.parseInt(separated5[1])
        val raceTime = 60 * minutes + seconds

        //Fuer jeden Rekord wird ueberprueft ob dieser unterboten wurde. Wenn ja wird eine Meldung auf dem Display ausgegeben, die Variable highscorelistData wird in richtiger Formatierung aktualisiert
        //      und die writeHighscores()-Funktion wird aufgerugen um die Datei mit den Highscores zu aktualisieren.
        if(raceTime < combTime1)
        {
            Toast.makeText(applicationContext, "New Highscore", Toast.LENGTH_SHORT).show()
            highscorelistData = playerName + " - " + formatter.format(checkMin).toString() + ":" +  formatter.format(checkSec).toString() + " - " +  formatter.format(minutes).toString() + ":" + formatter.format(seconds).toString() + " - " +
                    separated[0] + " - " + separated[1] +  " - " + separated[2] + " - " +
                    separated[3] + " - " + separated[4] +  " - " + separated[5] + " - " +
                    separated[6] + " - " + separated[7] +  " - " + separated[8] + " - " +
                    separated[9] + " - " + separated[10] +  " - " + separated[11]
            writeHighscores(applicationContext)
        }
        else if(raceTime < combTime2)
        {
            Toast.makeText(applicationContext, "New Second Place", Toast.LENGTH_SHORT).show()
            highscorelistData = separated[0] + " - " + separated[1] +  " - " + separated[2] + " - " +
                    playerName + " - " + formatter.format(checkMin).toString() + ":" +  formatter.format(checkSec).toString() + " - " +  formatter.format(minutes).toString() + ":" + formatter.format(seconds).toString() + " - " +
                    separated[3] + " - " + separated[4] +  " - " + separated[5] + " - " +
                    separated[6] + " - " + separated[7] +  " - " + separated[8] + " - " +
                    separated[9] + " - " + separated[10] +  " - " + separated[11]
            writeHighscores(applicationContext)
        }
        else if(raceTime < combTime3)
        {
            Toast.makeText(applicationContext, "New Third Place", Toast.LENGTH_SHORT).show()
            highscorelistData = separated[0] + " - " + separated[1] +  " - " + separated[2] + " - " +
                    separated[3] + " - " + separated[4] +  " - " + separated[5] + " - " +
                    playerName + " - " + formatter.format(checkMin).toString() + ":" +  formatter.format(checkSec).toString() + " - " +  formatter.format(minutes).toString() + ":" + formatter.format(seconds).toString() + " - " +
                    separated[6] + " - " + separated[7] +  " - " + separated[8] + " - " +
                    separated[9] + " - " + separated[10] +  " - " + separated[11]
            writeHighscores(applicationContext)
        }
        else if(raceTime < combTime4)
        {
            Toast.makeText(applicationContext, "New Fourth Place", Toast.LENGTH_SHORT).show()
            highscorelistData = separated[0] + " - " + separated[1] +  " - " + separated[2] + " - " +
                    separated[3] + " - " + separated[4] +  " - " + separated[5] + " - " +
                    separated[6] + " - " + separated[7] +  " - " + separated[8] + " - " +
                    playerName + " - " + formatter.format(checkMin).toString() + ":" +  formatter.format(checkSec).toString() + " - " +  formatter.format(minutes).toString() + ":" + formatter.format(seconds).toString() + " - " +
                    separated[9] + " - " + separated[10] +  " - " + separated[11]
            writeHighscores(applicationContext)
        }
        else if(raceTime < combTime5)
        {
            Toast.makeText(applicationContext, "New Fifth Place", Toast.LENGTH_SHORT).show()
            highscorelistData = separated[0] + " - " + separated[1] +  " - " + separated[2] + " - " +
                    separated[3] + " - " + separated[4] +  " - " + separated[5] + " - " +
                    separated[6] + " - " + separated[7] +  " - " + separated[8] + " - " +
                    separated[9] + " - " + separated[10] +  " - " + separated[11] + " - " +
                    playerName + " - " + formatter.format(checkMin).toString() + ":" +  formatter.format(checkSec).toString() + " - " +  formatter.format(minutes).toString() + ":" + formatter.format(seconds).toString() + " - " +
            writeHighscores(applicationContext)
        }
    }

    //Funktion Spielernamen festsetzen
    /*
    Das Textfeld usernameInput wird sichtbar gemacht und auf den Standardwert gesetzt.
    Mit Druecken des "Done"-Buttons wird der eingegebene Name in der Variable playerName gespeichert, falls das Textfeld nicht leer ist.
    Wird hinter dem Standardwert "Name:" nicht eingegeben wird die Funktion erneut aufgerufen, bis ein Spielername vorliegt.
    */
    private fun setName()
    {
        usernameInput.setText("Name: ")
        usernameInput.visibility = View.VISIBLE
        usernameInput.setOnEditorActionListener(
        {
            textView, i, keyEvent ->
            var handled = false
            if (i == EditorInfo.IME_ACTION_DONE)
            {
                val separatedplayerName = usernameInput.text.toString().split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if(separatedplayerName[1] != "")
                {
                    playerName = separatedplayerName[1]
                    usernameInput.visibility = View.GONE
                }
                else
                {
                    Toast.makeText(applicationContext, "Please enter a name!", Toast.LENGTH_SHORT).show()
                    setName()
                }
                handled = true
            }
            handled
        })
    }
    ////SONSTIGE FUNKTIONEN////
}
