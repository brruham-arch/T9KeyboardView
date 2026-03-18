# T9 IME — Keyboard Prediktif T9 untuk Android

Keyboard bergaya Nokia T9 dengan prediksi cerdas berbasis Trie,
dibangun dari Termux + GitHub Actions, tanpa PC.

## Fitur

- **T9 Predictive**: ketik urutan digit → saran kata otomatis dari kamus 50k
- **Multi-tap**: tap berulang → cycle A→B→C (klasik Nokia), timer 800ms auto-commit
- **Toggle mode**: tombol `⇄` untuk ganti mode sewaktu-waktu
- **Simpan kata favorit**: kata yang sering dipilih naik prioritas otomatis
- **Tanda baca**: tombol `1` → cycle . , ! ? ' -
- **Backspace hold**: tahan untuk hapus cepat

## Arsitektur

```
T9IMEService         ← InputMethodService (entry point Android)
├── T9KeyboardView   ← custom Canvas view, 3×4 grid
├── SuggestionBarView← horizontal scroll saran kata
├── T9InputController← state machine (PREDICTIVE / MULTITAP)
├── PredictionEngine ← Trie + T9 search + frequency ranking
└── UserWordStore    ← SharedPreferences untuk boost kata favorit
```

### Alur T9 Predictive

```
Tekan [4][6][6][3]
      ↓
PredictionEngine.predict("4663")
  → DFS Trie dengan constraint:
      depth 0: char ∈ {g,h,i}
      depth 1: char ∈ {m,n,o}
      depth 2: char ∈ {m,n,o}
      depth 3: char ∈ {d,e,f}
  → kandidat: home(9821), gone(3210), hone(412)…
  → boost kata yang sering dipilih: freq + hit×150
  → return top 8

SuggestionBar menampilkan: [home] gone hone …
Composing text = "home" (underline)
User tap "home" → commit "home " + catat di UserWordStore
```

### Alur Multi-tap

```
Tap [2]       → composing = "a"
Tap [2] lagi  → composing = "b"  (dalam 800ms)
Tap [2] lagi  → composing = "c"
Timer 800ms   → finishComposing → "c" committed
Tap [3]       → commitMultitap, mulai composing = "d"
```

## Setup dari Termux

### 1. Clone / Init repo

```bash
cd ~
git clone https://github.com/brruham-arch/T9KeyboardView
# atau buat baru:
mkdir t9ime && cd t9ime && git init
```

### 2. Salin file kamus

```bash
# Salin id_50k.txt yang sudah ada dari ClipKeys ke assets
cp ~/ClipKeys/app/src/main/assets/id_50k.txt \
   ~/t9ime/app/src/main/assets/id_50k.txt
```

Format `id_50k.txt` yang diharapkan (1 kata per baris):
```
yang 1234567
dan  987654
di   876543
```
Spasi sebagai separator, kolom 1 = kata, kolom 2 = frekuensi.

### 3. Salin file Gradle Wrapper dari proyek lain

```bash
cp -r ~/ClipKeys/gradle ~/t9ime/
cp ~/ClipKeys/gradlew ~/t9ime/
cp ~/ClipKeys/gradlew.bat ~/t9ime/
```

### 4. Push ke GitHub

```bash
cd ~/t9ime
git add .
git commit -m "feat: T9 IME initial"
git remote add origin https://github.com/brruham-arch/T9KeyboardView.git
git push -u origin main
```

### 5. Download APK dari GitHub Actions

- Buka repo di browser → tab **Actions**
- Klik workflow run terbaru → **Artifacts**
- Download `T9IME-debug-N.zip` → extract → install APK

### 6. Aktifkan keyboard

Settings → System → Language & Input → On-Screen Keyboard → **Manage keyboards** → aktifkan **T9 Keyboard**

## Pengembangan iteratif

Edit file di Termux:
```bash
nano app/src/main/java/com/brruham/t9ime/PredictionEngine.kt
git add . && git commit -m "fix: ..." && git push
```
→ GitHub Actions otomatis build ulang.

## Roadmap

- [ ] Bigram model (prediksi kata berikutnya berdasarkan konteks)
- [ ] Auto-kapitalisasi awal kalimat
- [ ] Shift key (huruf besar)
- [ ] Hapus satu kata (backspace long-press yang lebih pintar)
- [ ] Export/import user word database
- [ ] Angka-only mode (hold tombol → insert digit langsung)
