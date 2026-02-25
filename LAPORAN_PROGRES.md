# Laporan Progres Skripsi

**Judul Penelitian:**
Implementasi dan Perbandingan Tiga Arsitektur Large Language Model (LLM) pada Aplikasi Chatbot Kesehatan Mental Berbasis Android

**Nama:** \[Nama Mahasiswa\]
**NIM:** \[NIM\]
**Pembimbing:** \[Nama Dosen Pembimbing\]
**Tanggal Laporan:** 25 Februari 2026

---

## Daftar Isi

1. [Ringkasan Progres](#1-ringkasan-progres)
2. [Detail Progres per Fase](#2-detail-progres-per-fase)
3. [Arsitektur yang Diimplementasi](#3-arsitektur-yang-diimplementasi)
4. [Kendala yang Dihadapi](#4-kendala-yang-dihadapi)
5. [Target Selanjutnya](#5-target-selanjutnya)
6. [Timeline](#6-timeline)

---

## 1. Ringkasan Progres

| Fase | Keterangan | Status |
|------|------------|--------|
| Fase 1 | Integrasi llama.cpp (GGUF) — Penggantian MediaPipe | ✅ Selesai |
| Fase 2 | Modul Fitur Kesehatan Mental | ✅ Selesai |
| Fase 3 | Implementasi 3 Arsitektur | ✅ Selesai |
| Fase 4 | Sistem Benchmark Otomatis | ✅ Selesai |
| Fase 5 | Layar Pemilihan Arsitektur & Navigasi | ✅ Selesai |
| Fase 6 | Peningkatan UI Chat (darurat, cloud, metrik) | ✅ Selesai |
| Fase 7 | Layar Benchmark & Hasil Perbandingan | ✅ Selesai |
| Fase 8 | Dokumentasi Setup & Panduan Pengguna | ✅ Selesai |
| Fase 9 | Build & Pengujian di Perangkat Nokia 5.4 | 🔲 Belum |
| Fase 10 | Pengambilan Data Benchmark & Analisis | 🔲 Belum |

**Progres keseluruhan implementasi kode: 100% selesai.**
Tahap selanjutnya adalah pengujian pada perangkat nyata dan pengambilan data untuk penulisan skripsi.

---

## 2. Detail Progres per Fase

### Fase 1 — Integrasi Model GGUF via llama.cpp

Repositori dasar yang digunakan (*Gemma-on-Android* oleh NSTiwari) menggunakan **MediaPipe GenAI** dengan format model `.bin`. Setelah diteliti, MediaPipe tidak mendukung format GGUF yang diperlukan untuk model Gemma 2B-IT terkuantisasi.

**Yang dilakukan:**
- Mengganti seluruh lapisan inferensi dari MediaPipe ke **llama.cpp** melalui JNI (Java Native Interface) dan Android NDK
- Menulis JNI bridge dalam C++17 (`llama_bridge.cpp`) yang menyediakan fungsi: load model, generate sync, generate stream, token count, dan free model
- Mengintegrasikan llama.cpp sebagai submodule Git di dalam proyek
- Mengkonfigurasi CMake untuk cross-compile ke arsitektur `arm64-v8a` (sesuai Nokia 5.4)
- Model yang digunakan: **Gemma 2B-IT Q4\_K\_M** (~1,5 GB, format GGUF)

**File yang dibuat/dimodifikasi:**
- `app/src/main/cpp/CMakeLists.txt` *(baru)*
- `app/src/main/cpp/llama_bridge.cpp` *(baru)*
- `llm/LlamaCppNative.kt` *(baru)*
- `llm/LlamaCppModel.kt` *(baru)*
- `llm/PromptFormatter.kt` *(baru)*
- `app/build.gradle.kts` *(dimodifikasi)*
- `AndroidManifest.xml` *(dimodifikasi)*

---

### Fase 2 — Modul Fitur Kesehatan Mental

**Yang dilakukan:**
Empat modul fitur utama diimplementasi sebagai komponen independen yang dapat digunakan oleh ketiga arsitektur:

| Modul | Fungsi |
|-------|--------|
| `EmergencyDetector` | Deteksi kata kunci krisis (Bahasa Indonesia & Inggris), mengembalikan confidence score dan kata kunci yang cocok |
| `Anonymizer` | Penghapusan PII (Personally Identifiable Information) berbasis regex: nomor telepon, email, NIK 16 digit, URL, IP, tanggal lahir, gelar nama |
| `Summarizer` | Meringkas konteks percakapan menggunakan LLM jika token melebihi ambang batas (512 token) sebelum dikirim ke cloud |
| `CloudFallback` | Menyiapkan payload JSON (teks teranonimisasi + ringkasan + info perangkat) — mode mock, tidak ada API call nyata |

Kata kunci darurat yang dideteksi mencakup istilah Bahasa Indonesia seperti:
*"bunuh diri", "mau mati", "tidak mau hidup", "ingin mengakhiri hidup"*, dll.

---

### Fase 3 — Implementasi 3 Arsitektur

Tiga arsitektur diimplementasi mengikuti antarmuka `ChatArchitecture` yang seragam sehingga dapat dipertukarkan dan dibandingkan secara adil.

**Perbedaan antar arsitektur:**

| Aspek | Multitask | Separate | Pipeline |
|-------|-----------|----------|----------|
| Deteksi darurat | LLM (dalam satu prompt) | Keyword-based | Keyword-based (tahap 1) |
| Anonimisasi | LLM (dalam satu prompt) | Regex | Regex (tahap 3) |
| Percakapan | LLM (satu panggilan gabungan) | LLM (prompt khusus chat) | LLM (tahap 2) |
| Panggilan LLM/pesan | 1 (prompt besar) | 1 (prompt ringan) | 1 (prompt ringan) |
| Keunggulan | Sederhana, satu panggilan | Modul terpisah, terukur | Tahap eksplisit, mudah ditelusuri |
| Kelemahan | Prompt panjang, latensi tinggi | Koordinasi modul | Latensi bergantung rantai tahap |

---

### Fase 4 — Sistem Benchmark Otomatis

**Yang dilakukan:**
- Dataset sintetis **20 sampel** (10 normal + 10 mengandung situasi darurat) diport dari eksperimen Python
- Sistem benchmark berjalan otomatis pada ketiga arsitektur secara berurutan

**Metrik yang dikumpulkan per sampel:**

| Kategori | Metrik |
|----------|--------|
| Keselamatan | Emergency Recall, Precision, F1-score, TP/FP/TN/FN |
| Privasi | Anonymization Rate, PII Leakage Rate |
| Performa | Total Latency, Latency per tahap, Time-to-First-Token |
| Efisiensi | Tokens/detik, Peak RAM (PSS) |

**Skor komposit (Overall Score):**
```
Score = 0.40 × Emergency Recall + 0.30 × Anonymization Rate + 0.30 × Latency Score
```

**Format ekspor hasil:**
- `raw_<timestamp>.json` — Data mentah per sampel
- `summary_<timestamp>.json` — Agregat per arsitektur
- `results_<timestamp>.csv` — Untuk Excel/Google Sheets
- `report_<timestamp>.md` — Tabel perbandingan Markdown

---

### Fase 5–7 — Antarmuka Pengguna (UI)

**Layar yang diimplementasi:**

1. **Layar Pemilihan Arsitektur** — Tiga kartu (Multitask / Separate / Pipeline) dengan penjelasan singkat + tombol "Jalankan Benchmark"
2. **Layar Loading** — Menampilkan nama arsitektur yang dipilih dan status pemuatan model
3. **Layar Chat** — Badge arsitektur aktif, gelembung pesan, strip metrik *real-time* (latensi, tokens/detik, RAM, latensi per tahap), tombol cloud upload, dialog darurat
4. **Layar Benchmark** — Progress bar, pilihan "Full (20 sampel)" atau "Quick (10 sampel)"
5. **Layar Hasil Benchmark** — Tabel perbandingan horizontal, kartu detail per arsitektur, arsitektur terbaik disorot emas, lokasi file ekspor + perintah `adb pull`

---

### Fase 8 — Dokumentasi

File `SETUP_AND_USER_GUIDE.md` dibuat mencakup:
- Prasyarat sistem (JDK 17, ADB, Android Studio, NDK r26+, CMake 3.22+, Git)
- Langkah clone repositori dan inisialisasi submodule llama.cpp
- Cara mengunduh model GGUF dari HuggingFace
- Konfigurasi Android Studio (SDK, NDK, CMake)
- Perintah build (`./gradlew assembleDebug`)
- Instalasi APK dan push model ke perangkat via `adb`
- Panduan penggunaan seluruh fitur aplikasi
- Panduan menjalankan dan mengambil hasil benchmark
- Troubleshooting 8 masalah umum beserta solusinya

---

## 3. Arsitektur yang Diimplementasi

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARSITEKTUR MULTITASK                         │
│                                                                 │
│  Input ──► Prompt Gabungan ──► LLM ──► Parse Output            │
│            (Darurat + Anonimisasi + Chat dalam satu prompt)     │
│                              │                                  │
│                    ┌─────────┴─────────┐                        │
│                 Darurat?           Respons Chat                 │
│                 Anonim Input                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    ARSITEKTUR SEPARATE                          │
│                                                                 │
│  Input ──► EmergencyDetector ──► Anonymizer ──► LLM            │
│            (Keyword-based)       (Regex)       (Chat-only)      │
│                │                    │               │           │
│            Darurat?            Anonim Input    Respons Chat     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    ARSITEKTUR PIPELINE                          │
│                                                                 │
│  Input ──► [Tahap 1] ──► [Tahap 2] ──► [Tahap 3]              │
│            Deteksi        LLM           Anonimisasi             │
│            Darurat        Chat          Regex                   │
│                │              │              │                  │
│            Darurat?      Respons Raw    Respons Akhir           │
│                                                                 │
│  Cloud:  Input ──► Anonimisasi ──► Summarizer ──► Payload      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Kendala yang Dihadapi

### Kendala 1 — Format Model Tidak Kompatibel dengan MediaPipe

**Masalah:**
Repositori dasar menggunakan MediaPipe GenAI yang hanya mendukung format model `.bin` (TensorFlow Lite). Model Gemma 2B-IT yang akan digunakan dalam penelitian ini tersedia dalam format GGUF (terkuantisasi), yang tidak dapat dibaca oleh MediaPipe.

**Solusi:**
Seluruh lapisan inferensi diganti dengan **llama.cpp** melalui JNI/NDK. Ini memerlukan penulisan kode C++ untuk JNI bridge dan konfigurasi CMake dari awal, namun memberikan fleksibilitas penuh untuk menggunakan format GGUF dengan berbagai level kuantisasi.

---

### Kendala 2 — Library NLP (spaCy) Tidak Tersedia di Android/Kotlin

**Masalah:**
Rencana awal menggunakan **spaCy NER** (Named Entity Recognition) untuk proses anonimisasi PII. Namun, spaCy adalah library Python dan tidak dapat diintegrasikan ke dalam aplikasi Android berbasis Kotlin.

**Solusi:**
Anonimisasi diimplementasi menggunakan **regex rule-based** yang diport dari kode Python eksperimen. Pola regex mencakup: nomor telepon Indonesia/internasional, email, NIK 16 digit, URL, alamat IP, tanggal lahir, dan gelar nama (Bahasa Indonesia dan Inggris). Sebagai diferensiasi penelitian, arsitektur **Multitask menggunakan LLM** untuk anonimisasi, sementara Separate dan Pipeline menggunakan regex.

---

### Kendala 3 — Tidak Ada Android SDK di Lingkungan Pengembangan Utama (Windows)

**Masalah:**
Komputer pengembangan utama menggunakan Windows tanpa Android SDK terpasang, sehingga kompilasi, emulasi, dan pengujian APK tidak dapat dilakukan secara langsung selama penulisan kode.

**Solusi:**
Seluruh kode ditulis dengan memperhatikan kompatibilitas dan best practice Android secara ketat, dengan target build di **Ubuntu 22.04** yang telah dikonfigurasi Android SDK-nya. Dokumentasi setup Ubuntu (`SETUP_AND_USER_GUIDE.md`) disiapkan untuk memperlancar proses build di mesin yang tepat.

---

### Kendala 4 — Perubahan API Internal llama.cpp

**Masalah:**
API internal llama.cpp berubah antar versi. Fungsi `llama_batch_add` yang umum dicontohkan di dokumentasi memerlukan file `common.h` dari repositori llama.cpp, yang menyebabkan ketergantungan kompilasi yang tidak diinginkan.

**Solusi:**
Menulis fungsi helper `batch_add_token()` secara lokal di dalam `llama_bridge.cpp` sehingga tidak bergantung pada file eksternal dari llama.cpp selain header publiknya. Ini juga membuat kode lebih stabil terhadap perubahan versi llama.cpp di masa mendatang.

---

### Kendala 5 — Dua Mode Generasi yang Berbeda (Streaming vs Sinkron)

**Masalah:**
Mode chat memerlukan **streaming token** (token ditampilkan satu per satu secara real-time) untuk pengalaman pengguna yang baik. Namun, sistem benchmark memerlukan generasi **sinkron** (blocking) agar pengukuran latensi bersih tanpa overhead callback asinkron.

**Solusi:**
Dua jalur generasi diimplementasi di `LlamaCppModel`:
- `generateStream()` — mengalirkan token via `SharedFlow` untuk UI chat
- `generateSync()` — mengembalikan teks lengkap + metrik waktu untuk benchmark

---

## 5. Target Selanjutnya

### Target Jangka Pendek (Minggu Ini)

- [ ] **Build APK di Ubuntu 22.04** — Mengkompilasi proyek menggunakan Android Studio di Ubuntu, memastikan llama.cpp berhasil di-*cross-compile* untuk `arm64-v8a`
- [ ] **Push model ke Nokia 5.4** — Mengunduh Gemma 2B-IT Q4\_K\_M dari HuggingFace dan mendorongnya ke perangkat via perintah `adb push`
- [ ] **Pengujian fungsional end-to-end** — Memverifikasi:
  - Model berhasil dimuat dan menghasilkan respons pada ketiga arsitektur
  - Streaming token berjalan lancar di mode chat
  - Deteksi darurat memunculkan dialog dengan nomor hotline
  - Tombol cloud upload menampilkan payload teranonimisasi (mode mock)

### Target Jangka Menengah (2–3 Minggu ke Depan)

- [ ] **Pengambilan data benchmark** — Menjalankan 20 sampel sintetis pada ketiga arsitektur di Nokia 5.4 dan mengekspor hasilnya (JSON, CSV, Markdown)
- [ ] **Analisis hasil benchmark** — Membandingkan:
  - Emergency Recall, Precision, F1-score antar arsitektur
  - PII Anonymization Rate
  - Latensi rata-rata dan puncak RAM
  - Skor komposit keseluruhan
- [ ] **Penulisan Bab 4 (Hasil dan Pembahasan)** — Menyusun analisis kuantitatif berdasarkan data benchmark yang dikumpulkan

### Target Jangka Panjang

- [ ] **Penulisan Bab 5 (Kesimpulan dan Saran)** — Menyimpulkan arsitektur terbaik berdasarkan data dan konteks penggunaan
- [ ] **Revisi dan penyempurnaan laporan skripsi** — Berdasarkan masukan dosen pembimbing
- [ ] **Sidang skripsi**

---

## 6. Timeline

```
Jan 2026    Feb 2026    Mar 2026    Apr 2026
──────────────────────────────────────────────►
│           │           │           │
▼           ▼           ▼           ▼
[====== Implementasi Kode ======]
            [= Build & Test =]
                        [= Analisis Data =]
                        [=== Penulisan Bab 4–5 ===]
                                    [= Sidang =]
```

| Periode | Kegiatan |
|---------|----------|
| Januari 2026 | Perancangan arsitektur, implementasi llama.cpp, modul fitur |
| Februari 2026 | Implementasi 3 arsitektur, sistem benchmark, UI, dokumentasi |
| Maret 2026 | Build di Ubuntu, pengujian Nokia 5.4, pengambilan data benchmark |
| Maret–April 2026 | Analisis data, penulisan Bab 4 & 5, revisi laporan |
| April 2026 | Sidang skripsi |

---

*Laporan ini dibuat pada 25 Februari 2026.*
*Seluruh kode tersedia di repositori Git lokal dengan 8 commit terstruktur per fase pengembangan.*
