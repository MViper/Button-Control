# 📖 ButtonControl – Vollständige Anleitung

> **Version:** 1.8 · **Autor:** M_Viper · **Spigot:** [spigotmc.org/resources/127702](https://www.spigotmc.org/resources/127702/)

---

## 📋 Inhaltsverzeichnis

1. [Was ist ButtonControl?](#was-ist-buttoncontrol)
2. [Controller-Typen im Überblick](#controller-typen-im-überblick)
3. [Steuerbare Blöcke](#steuerbare-blöcke)
4. [Schritt-für-Schritt: Erste Schritte](#schritt-für-schritt-erste-schritte)
5. [Rezepte – Controller herstellen](#rezepte--controller-herstellen)
6. [Controller platzieren & verbinden](#controller-platzieren--verbinden)
7. [Bewegungsmelder konfigurieren](#bewegungsmelder-konfigurieren)
8. [Geheimwand (Secret Wall)](#geheimwand-secret-wall)
9. [Zeitplan konfigurieren](#zeitplan-konfigurieren)
10. [Controller umbenennen](#controller-umbenennen)
11. [Trust-System](#trust-system)
12. [Alle Befehle im Überblick](#alle-befehle-im-überblick)
13. [Berechtigungen](#berechtigungen)
14. [Konfigurationsdateien](#konfigurationsdateien)
15. [Häufige Fragen & Probleme](#häufige-fragen--probleme)

---

## Was ist ButtonControl?

ButtonControl erlaubt es Spielern, **Türen, Eisentüren, Zauntore, Falltüren, Redstone- und Kupferlampen, Gitter, Creaking Heart, Spender/Werfer, Notenblöcke und Glocken** mit einem selbst hergestellten Controller zu steuern – ohne Redstone-Kabel, ohne Mechanismen.

**Mögliche Controller-Typen:**
- Holz- und Steinbuttons aller Arten
- Tageslichtsensoren (öffnen/schließen automatisch nach Tageszeit)
- Bewegungsmelder (Tripwire Hook – reagiert auf Spieler **und** Mobs)
- Teppich-Sensoren (reagieren **nur** auf Spieler)
- Schilder (wandmontierte Controller)

---

## Controller-Typen im Überblick

| Symbol | Controller | Auslöser | Besonderheit |
|--------|-----------|---------|--------------|
| 🔘 | **Steuer-Button** | Rechtsklick | Manuelles Öffnen/Schließen |
| ☀️ | **Steuer-Tageslichtsensor** | Tag/Nacht-Wechsel | Automatisch, kein Klick nötig |
| 🪝 | **Steuer-Bewegungsmelder** | Spieler + Mobs in der Nähe | Einstellbarer Radius & Verzögerung |
| 🟫 | **Steuer-Teppich** | Nur Spieler in der Nähe | Mobs lösen ihn **nicht** aus |
| 🪧 | **Steuer-Schild** | Rechtsklick | Wandmontiert, unsichtbarer Controller |

---

## Steuerbare Blöcke

Folgende Blöcke können mit einem Controller verbunden werden:

| Block | Funktion | Anmerkung |
|-------|---------|-----------|
| Alle Holztüren | Öffnen / Schließen | Inkl. Doppeltüren (beide Hälften werden automatisch erkannt) |
| **Eisentür** | Öffnen / Schließen | Kein Redstone-Signal nötig – funktioniert direkt |
| Alle Holz-Falltüren | Öffnen / Schließen | |
| **Eisenfalltür** | Öffnen / Schließen | Wie Eisentür, kein Redstone nötig |
| Alle Zauntore | Öffnen / Schließen | |
| Redstone-Lampe + Kupferlampen | Ein / Ausschalten | Unterstützt normale, verwitterte und gewachste Kupferlampen |
| Creaking Heart (Knarrherz) | Aktivieren / Deaktivieren | Bleibt aktiv bis manuell ausgeschaltet |
| Gitter (`*_GRATE`) + Eisenstangen | Öffnen / Schließen | Öffnen = temporär frei (AIR), Schließen = Originalmaterial wird wiederhergestellt |
| Spender (Dispenser) | Auslösen | Kann per Zeitplan als Show laufen |
| Werfer (Dropper) | Auslösen | Kann per Zeitplan als Show laufen |
| Notenblock | Klingelton abspielen | Instrument pro Spieler einstellbar |
| Glocke | Läuten | |

---

## Schritt-für-Schritt: Erste Schritte

### 1. Controller herstellen
Stelle einen Controller in der Werkbank her (siehe [Rezepte](#rezepte--controller-herstellen)).

### 2. Controller platzieren
Halte den hergestellten Controller in der Hand und **platziere ihn** wie einen normalen Block. Du erhältst die Nachricht: `§aController platziert.`

### 3. Blöcke verbinden
Halte den Controller weiterhin **in der Hand** (nicht platziert!) und **klicke mit Rechtsklick** auf einen Zielblock (Tür, Lampe usw.). Du erhältst: `§aBlock verbunden.`

> Du kannst denselben Controller mit mehreren Blöcken verbinden – einfach nacheinander alle Zielblöcke anklicken.

### 4. Controller benutzen
Klicke mit **Rechtsklick** auf den platzierten Controller. Alle verbundenen Blöcke werden gleichzeitig umgeschaltet.

---

## Rezepte – Controller herstellen

Alle Rezepte folgen demselben Muster: **3× dasselbe Material in der mittleren Spalte** der Werkbank.

```
[ ]  [X]  [ ]
[ ]  [X]  [ ]
[ ]  [X]  [ ]
```

| Ergebnis | Zutat (X) |
|---------|---------|
| Steuer-Button (Eiche) | Eichen-Button |
| Steuer-Button (Stein) | Stein-Button |
| Steuer-Button (jede Holzart) | Entsprechender Button |
| Steuer-Tageslichtsensor | Tageslichtsensor |
| Steuer-Notenblock | Notenblock |
| Steuer-Bewegungsmelder | Tripwire Hook |
| Steuer-Schild | Eichenschild |
| Steuer-Teppich (Weiß) | Weißer Teppich |
| Steuer-Teppich (alle Farben) | Entsprechender Teppich |

> Alle 16 Teppichfarben können als Sensor verwendet werden – sie verhalten sich identisch, nur die Farbe unterscheidet sich.

---

## Controller platzieren & verbinden

### Platzieren
1. Halte den fertigen Controller in der Hand
2. Platziere ihn wie einen normalen Block auf einer Fläche
3. ✅ `Controller platziert.`

### Verbinden
1. Halte den **nicht platzierten** Controller in der Hand
2. Klicke mit **Rechtsklick** auf einen Zielblock
3. ✅ `Block verbunden.`

### Grenzen pro Controller
| Block-Typ | Standard-Limit |
|-----------|---------------|
| Türen (inkl. Eisentür) | 20 |
| Zauntore | 20 |
| Falltüren (inkl. Eisen) | 20 |
| Redstone- und Kupferlampen | 50 |
| Spender | 20 |
| Werfer | 20 |
| Notenblöcke | 10 |
| Glocken | 5 |

> Limits können vom Server-Admin in `config.yml` angepasst werden.

### Controller abbauen
Schlage den Controller ab. Nur der **Besitzer** oder ein Admin darf ihn abbauen.

Beim Abbau werden jetzt alle zugehörigen Daten automatisch entfernt (sowohl in `data.yml` als auch in MySQL):
- Verbindungen
- Trust/Public-Status
- Zeitplan
- Bewegungsmelder-Einstellungen
- Secret-Wall-Blöcke, Delay und Animation

> ⚠️ Wenn ein verbundener Block (Tür, Lampe usw.) abgebaut wird, entfernt ButtonControl den Eintrag **automatisch** aus der Liste.

### Verbundene Blöcke anzeigen
Sieh einen platzierten Controller an (max. 5 Blöcke Entfernung) und tippe:
```
/bc list
```
Du siehst alle verbundenen Blöcke mit Typ, Koordinaten und Welt sowie den aktuellen Zeitplan.

---

## Bewegungsmelder konfigurieren

### Tripwire Hook (Standard-Bewegungsmelder)
- Erkennt **Spieler und Mobs** in einem einstellbaren Radius
- Öffnet verbundene Blöcke sobald jemand in der Nähe ist
- Schließt sie automatisch nach einer konfigurierbaren Verzögerung

### Teppich-Sensor
- Funktioniert genauso wie der Tripwire Hook
- Erkennt jedoch **nur Spieler** – Tiere, Monster und andere Mobs lösen ihn **nicht** aus
- Ideal für Eingänge wo Tiere nicht versehentlich Türen öffnen sollen

### GUI öffnen
Klicke mit **Rechtsklick** auf einen platzierten Bewegungsmelder oder Teppich-Sensor.

```
┌─────────────────────────────┐
│  Bewegungsmelder-Einstellungen  │
│                               │
│  [🧭 Radius]   [  ]   [🕐 Verzögerung]  │
│                               │
│           [💚 Speichern]          │
└─────────────────────────────┘
```

| Taste | Aktion |
|-------|--------|
| **Linksklick** auf Kompass | Radius +0,5 Blöcke |
| **Rechtsklick** auf Kompass | Radius −0,5 Blöcke |
| **Linksklick** auf Uhr | Verzögerung +1 Sekunde |
| **Rechtsklick** auf Uhr | Verzögerung −1 Sekunde |
| **Klick** auf Smaragd | Speichern & Schließen |

**Wertebereiche:**
- Radius: 0,5 – 20,0 Blöcke
- Verzögerung: 1 – 30 Sekunden

Secret-Wall-Verhalten mit Sensoren:
- Bewegungsmelder/Teppich kann auch eine Secret Wall öffnen
- Bei Erkennung: Wall öffnet
- Nach Ablauf der Verzögerung ohne Erkennung: Wall schließt

---

## Geheimwand (Secret Wall)

Mit Secret Walls kannst du Blöcke eines Eingangs temporär „wegfahren“ lassen und automatisch wiederherstellen.

### Einrichtung
1. Controller ansehen und auswählen:
```
/bc secret select
```
2. Geheimblöcke nacheinander hinzufügen (jeweils Block ansehen):
```
/bc secret add
```
3. Optional Animation setzen:
```
/bc secret animation <instant|wave|reverse|center>
```
4. Optional Wiederherstellungszeit setzen:
```
/bc secret delay <sekunden>
```
5. Status prüfen:
```
/bc secret info
```

### Animationen
- `instant`: alle Blöcke gleichzeitig
- `wave`: der Reihe nach
- `reverse`: umgekehrte Reihenfolge
- `center`: von der Mitte nach außen (und beim Schließen außen nach innen)

Hinweis:
- Secret Walls funktionieren auch ohne normale verbundene Blöcke.
- Tageslichtsensoren und Bewegungsmelder können Secret Walls automatisch öffnen/schließen.

---

## Zeitplan konfigurieren

Der Zeitplan erlaubt es, verbundene Blöcke **automatisch zu einer bestimmten Ingame-Uhrzeit** zu öffnen und zu schließen – ohne dass jemand klicken muss.

**Beispielanwendungen:**
- Dorftor öffnet automatisch morgens um 07:00, schließt abends um 19:00
- Laternenpfahl-Lampen schalten sich nachts ein, tagsüber aus
- Geschäfts-Eingang öffnet nur zu "Öffnungszeiten"
- Feuerwerk-Show startet abends automatisch und endet nachts (mit Werfern/Spendern)

### GUI öffnen
Sieh den Controller an und tippe:
```
/bc schedule
```

```
┌─────────────────────────────────┐
│     Zeitplan-Einstellungen       │
│                                   │
│ [⏱ Delay] [⚖ Modus] [🔧 An/Aus] │
│ [🟢 Öffnungszeit]          [🔴 Schließzeit] │
│                                   │
│           [💚 Speichern]              │
└─────────────────────────────────┘
```

| Taste | Aktion |
|-------|--------|
| **Linksklick** auf Zeit-Item | +1 Stunde |
| **Rechtsklick** auf Zeit-Item | −1 Stunde |
| **Shift + Linksklick** | +15 Minuten |
| **Shift + Rechtsklick** | −15 Minuten |
| **Link/Rechtsklick** auf Delay | ±1 Tick (Shift: ±5) |
| **Klick** auf Modus | `gleichzeitig` / `nacheinander` umschalten |
| **Klick** auf Hebel/Strauch | Zeitplan ein-/ausschalten |
| **Klick** auf Smaragd | Speichern & Schließen |

> ⚠️ Die Zeiten sind **Ingame-Zeiten** (ein Minecraft-Tag = 20 Minuten Echtzeit).
> Beispiel: "07:00" = Minecraft-Sonnenaufgang, "19:00" = Sonnenuntergang.

> Hinweis zu Werfer/Spender: Wenn ein Controller einen aktiven Zeitplan hat, lösen verbundene Werfer/Spender während des Zeitfensters automatisch aus.
> - Modus `gleichzeitig`: Alle verbundenen Werfer/Spender schießen pro Zyklus zusammen.
> - Modus `nacheinander`: Pro Zyklus schießt ein Gerät, dann rotiert es zum nächsten.
> - Die Delay-Anzeige zeigt Ticks und Sekunden (z.B. `20 Ticks (1.00s)`).

**Über Mitternacht:** Zeitpläne die über Mitternacht gehen (z.B. Öffnen 22:00, Schließen 04:00) werden korrekt erkannt.

---

## Controller umbenennen

Du kannst jedem Controller einen eigenen Namen geben, der bei `/bc list` angezeigt wird.

```
/bc rename <Name>
```

**Beispiele:**
```
/bc rename Haupteingang
/bc rename Scheunentür Nordseite
/bc rename Licht Wohnraum
```

> Maximale Länge: 32 Zeichen. Leerzeichen sind erlaubt. Farbcodes mit § werden unterstützt.

---

## Trust-System

Mit dem Trust-System kannst du anderen Spielern erlauben, **deinen Controller zu benutzen** – ohne dass sie ihn verwalten oder abbauen dürfen.

### Spieler hinzufügen
Sieh den Controller an und tippe:
```
/bc trust <Spielername>
```
Der Spieler darf nun den Controller benutzen (auch wenn er offline ist, wenn er vorher schon einmal auf dem Server war).

### Spieler entfernen
```
/bc untrust <Spielername>
```

### Controller öffentlich machen
```
/bc public
```
Jeder Spieler auf dem Server kann den Controller nun benutzen – kein Trust nötig.

### Controller privat machen
```
/bc private
```
Nur du (und vertraute Spieler) können den Controller benutzen.

### Aktuellen Status anzeigen
```
/bc list
```
Zeigt unter anderem ob der Controller öffentlich oder privat ist.

---

## Alle Befehle im Überblick

| Befehl | Beschreibung | Berechtigung |
|--------|-------------|-------------|
| `/bc info` | Plugin-Version und Statistiken anzeigen | Jeder |
| `/bc list` | Verbundene Blöcke des angesehenen Controllers anzeigen | Besitzer / Trusted / Admin |
| `/bc rename <Name>` | Controller umbenennen (Controller ansehen) | Besitzer / Admin |
| `/bc schedule` | Zeitplan-GUI öffnen (Controller ansehen) | Besitzer / Admin |
| `/bc secret select` | Secret-Controller auswählen (alternativ Blickerkennung) | Besitzer / Admin |
| `/bc secret add` | Angesehenen Block als Geheimblock hinzufügen | Besitzer / Admin |
| `/bc secret remove` | Angesehenen Geheimblock entfernen | Besitzer / Admin |
| `/bc secret clear` | Alle Geheimblöcke des Controllers löschen | Besitzer / Admin |
| `/bc secret delay <Sekunden>` | Auto-Restore-Zeit für Secret Wall setzen | Besitzer / Admin |
| `/bc secret animation <instant\|wave\|reverse\|center>` | Secret-Wall-Animation setzen | Besitzer / Admin |
| `/bc secret info` | Secret-Wall-Konfiguration anzeigen | Besitzer / Admin |
| `/bc note <Instrument>` | Notenblock-Instrument ändern | `buttoncontrol.note` |
| `/bc trust <Spieler>` | Spieler darf Controller benutzen | Besitzer / Admin |
| `/bc untrust <Spieler>` | Berechtigung entziehen | Besitzer / Admin |
| `/bc public` | Controller für alle freigeben | Besitzer / Admin |
| `/bc private` | Controller nur für Besitzer & Trusted | Besitzer / Admin |
| `/bc reload` | Konfiguration neu laden | `buttoncontrol.reload` |

### Verfügbare Instrumente für `/bc note`

```
PIANO          BASS_DRUM      SNARE_DRUM     STICKS
BASS_GUITAR    FLUTE          BELL           CHIME
GUITAR         XYLOPHONE      IRON_XYLOPHONE COW_BELL
DIDGERIDOO     BIT            BANJO          PLING
```

**Beispiel:**
```
/bc note FLUTE
```

---

## Berechtigungen

| Permission | Beschreibung | Standard |
|-----------|-------------|---------|
| `buttoncontrol.admin` | Zugriff auf **alle** Controller (Bypass) | OP |
| `buttoncontrol.reload` | `/bc reload` ausführen | OP |
| `buttoncontrol.note` | Instrument mit `/bc note` ändern | Alle |
| `buttoncontrol.trust` | Trust-System verwenden | Alle |
| `buttoncontrol.update` | Update-Benachrichtigungen erhalten | OP |

### Admin-Bypass (`buttoncontrol.admin`)
Admins mit dieser Berechtigung können:
- Jeden Controller benutzen (auch private)
- Jeden Controller verwalten (trust, rename, schedule, public/private)
- Jeden Controller abbauen
- Alle verbundenen Blöcke einsehen (`/bc list`)

---

## Konfigurationsdateien

### `config.yml` – Hauptkonfiguration

```yaml
# Maximale Anzahl verbundener Blöcke pro Controller
max-doors: 20          # Holz- und Eisentüren
max-lamps: 50          # Redstone-Lampen
max-noteblocks: 10     # Notenblöcke
max-gates: 20          # Zauntore
max-trapdoors: 20      # Holz- und Eisenfalltüren
max-bells: 5           # Glocken
max-dispensers: 20     # Spender
max-droppers: 20       # Werfer

# Notenblock-Einstellungen
default-note: "PIANO"           # Standard-Instrument
double-note-enabled: true       # Zweiter Ton aktiviert
double-note-delay-ms: 1000      # Abstand zwischen den Tönen (ms)

# Bewegungsmelder-Standardwerte (überschreibbar per GUI pro Sensor)
motion-detection-radius: 5.0    # Erkennungsradius in Blöcken
motion-close-delay-ms: 5000     # Verzögerung vor dem Schließen (ms)
motion-trigger-cooldown-ms: 2000 # Mindestzeit zwischen zwei Auslösungen
timed-container-interval-ticks: 40 # Legacy-Fallback für alte Zeitpläne ohne gespeicherten Delay-Wert
timed-container-shot-delay-ticks: 2 # Standard-Delay zwischen Schüssen im Zeitplan
timed-container-trigger-mode: simultaneous # Standardmodus: simultaneous oder sequential

# Sounds beim Öffnen/Schließen
sounds:
  enabled: true
  door-open:       BLOCK_WOODEN_DOOR_OPEN
  door-close:      BLOCK_WOODEN_DOOR_CLOSE
  iron-door-open:  BLOCK_IRON_DOOR_OPEN
  iron-door-close: BLOCK_IRON_DOOR_CLOSE
  lamp-on:         BLOCK_LEVER_CLICK
  lamp-off:        BLOCK_LEVER_CLICK
```

### `lang.yml` – Nachrichten anpassen

Alle Spielernachrichten können in `lang.yml` geändert werden. Farbcodes mit `§` sind überall unterstützt.

```yaml
tueren-geoeffnet: "§aTüren wurden geöffnet."
controller-platziert: "§aController platziert."
# ... usw.
```

### `data.yml` – Spielerdaten

Diese Datei wird **automatisch** verwaltet und sollte nicht manuell bearbeitet werden. Sie enthält alle Controller-Positionen, Verbindungen, Trust-Einstellungen, Zeitpläne, Bewegungsmelder-Settings und Secret-Wall-Daten.

---

## Häufige Fragen & Probleme

**❓ Ich habe einen Controller platziert, aber beim Klicken passiert nichts.**
→ Du musst erst Blöcke verbinden. Halte den Controller **in der Hand** (nicht den platzierten Block anklicken) und klicke auf Türen, Lampen usw.

**❓ Ich kann den Controller eines anderen Spielers nicht benutzen.**
→ Der Controller ist privat. Bitte den Besitzer, dich per `/bc trust <DeinName>` hinzuzufügen oder den Controller mit `/bc public` zu öffnen.

**❓ Ich kann den Controller nicht abbauen.**
→ Nur der Besitzer (oder ein Admin mit `buttoncontrol.admin`) darf einen Controller abbauen.

**❓ Die Eisentür öffnet sich nicht.**
→ Stelle sicher, dass die Eisentür tatsächlich mit dem Controller verbunden ist. Halte den Controller in der Hand und klicke auf die Eisentür (untere Hälfte). Die obere Hälfte wird automatisch mitgenommen.

**❓ Der Bewegungsmelder schließt nicht nach der Zeit.**
→ Prüfe den Wert `motion-close-delay-ms` in der GUI (Rechtsklick auf den Sensor) oder in `config.yml`. Standardmäßig sind es 5 Sekunden.

**❓ Der Teppich-Sensor reagiert auf Mobs.**
→ Nur der **Steuer-Teppich** (hergestellt mit dem Rezept) erkennt nur Spieler. Ein normaler Teppich ist kein Controller.

**❓ Der Zeitplan funktioniert nicht.**
→ Stelle sicher, dass der Zeitplan in der ScheduleGUI **aktiviert** ist (grüner Hebel, nicht Strauch). Öffne die GUI mit `/bc schedule` und prüfe den Ein/Aus-Status.

**❓ Werfer/Spender schießen zu langsam oder zu schnell im Zeitplan.**
→ Stelle den Delay direkt in `/bc schedule` ein (GUI zeigt Ticks + Sekunden). Für globale Standardwerte passe `timed-container-shot-delay-ticks` in `config.yml` an und führe `/bc reload` aus.

**❓ 2 Werfer/Spender am selben Controller laufen nicht gleich.**
→ Öffne `/bc schedule` und stelle den Modus auf `gleichzeitig`. Im Modus `nacheinander` rotieren die Geräte absichtlich.

**❓ `/bc list` zeigt "Keine Blöcke verbunden".**
→ Entweder wurde der Controller noch nie mit Blöcken verbunden, oder alle verbundenen Blöcke wurden abgebaut (werden automatisch entfernt).

**❓ Wie sehe ich ob ein Controller einen Zeitplan hat?**
→ `/bc list` zeigt ganz unten den aktiven Zeitplan mit Öffnungs- und Schließzeit an.

**❓ Kann ich mehrere Controller auf dieselbe Tür zeigen lassen?**
→ Ja. Du kannst z.B. einen Button-Controller und einen Bewegungsmelder mit derselben Tür verbinden.

---

*Diese Anleitung bezieht sich auf ButtonControl v1.8. Für ältere Versionen können einzelne Funktionen abweichen.*