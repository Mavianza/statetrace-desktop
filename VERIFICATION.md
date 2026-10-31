# Hasil pemeriksaan aktual StateTrace 1.0.0

Dijalankan pada **1 November 2026** di Linux dengan **OpenJDK 17.0.20** dan Python 3.12.14.

| Pemeriksaan | Hasil |
| --- | --- |
| `java Build.java test` | Build berhasil dengan `--release 17 -encoding UTF-8 -Xlint:all -Werror`; **34 tes lulus, 0 gagal**. |
| Oracle bahasa pada tes Java | **4.094 pemeriksaan**: 2.047 string biner panjang 0–10, masing-masing diperiksa terhadap suffix `01` dan paritas jumlah `1`. Seluruh hasil cocok. |
| Tes panel Swing pada EDT | Tombol run/step/next, hasil diterima/ditolak, string kosong, pergantian contoh, invalidasi hasil lama, input salah, penyimpanan/pemuatan definisi, dan ekspor diuji dalam mode headless. |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/smoke.py` | **64 kasus bahasa melalui proses JAR nyata + 9 pemeriksaan integrasi lulus**. Termasuk CSV yang dibaca kembali memakai parser Python, penolakan overwrite, Unicode astral, transisi hilang, file hilang, dan GUI tanpa layar. |
| Struktur JAR | Manifest menunjuk `statetrace.Main`; seluruh `.class` memiliki major version 61, yaitu target Java 17; integritas ZIP JAR valid. |
| `java -Djava.awt.headless=true -jar dist/statetrace.jar render docs/statetrace.png` | PNG panel Swing **1100×780** terbentuk. Gambar diperiksa secara visual, termasuk judul kolom tabel, definisi contoh, jejak `1101`, dan verdict `DITERIMA`. |

Data tes Java/Python dibuat dalam direktori sementara dan dibersihkan. Source lengkap, JAR siap pakai, dua definisi contoh, launcher, dan pratinjau panel disertakan.

**Batas verifikasi:** lingkungan ini tidak menyediakan display server/Xvfb. Pengujian komponen GUI dan render panel bukan pengujian jendela native. Membuka jendela desktop, interaksi manual file chooser, launcher `.bat` pada Windows, serta tampilan native Windows/macOS belum diuji. Tidak ada klaim bahwa pemeriksaan tersebut sudah dilakukan.
