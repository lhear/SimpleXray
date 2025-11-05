# 🚀 PR Oluşturma - Hızlı Yöntem

## Adım 1: Bu Linke Tıkla

**Direkt PR oluşturma linki:**
👉 **https://github.com/halibiram/SimpleXray/compare/main...test/ai-fixer-bot**

## Adım 2: PR Formunu Doldur

**Başlık:**

```
test: AI Fixer Bot test
```

**Açıklama (kopyala-yapıştır):**

```markdown
Bu PR AI Fixer Bot'u test etmek için oluşturuldu.

## Test Dosyası

- `test_ai_fixer_change.cpp` - Kasıtlı olarak kod hataları içeriyor:
  - ❌ JNI memory leak (ReleaseByteArrayElements eksik)
  - ❌ Format specifier mismatch (%d vs %zu)
  - ❌ Unused parameter (void cast eksik)
  - ❌ Null check eksik

## Beklenen Sonuç

AI Fixer Bot bu sorunları tespit edip:

- Inline yorumlar yapmalı
- `ai_report.json` oluşturmalı
- `auto.patch` üretmeli (eğer düzeltmeler varsa)

## Not

Bu dosya test sonrası silinecek.
```

## Adım 3: "Create pull request" Butonuna Tıkla

PR oluşturulduğunda otomatik olarak workflow'lar çalışacak!

---

## ⚠️ Önemli: GitHub Secrets Kontrolü

PR oluşturmadan **önce** kontrol et:

1. GitHub'da repository sayfasına git
2. **Settings** → **Secrets and variables** → **Actions**
3. `OPENAI_API_KEY` secret'ının eklendiğinden emin ol
4. Eğer yoksa ekle:
   - Name: `OPENAI_API_KEY`
   - Secret: OpenAI API anahtarın

---

## 📊 Workflow'lar

PR oluşturulduğunda şu workflow'lar otomatik çalışacak:

1. **AI Inline Code Fixer** (`inline-fixer.yml`)

   - PR'da inline yorumlar yapar
   - Her değişiklik satırını analiz eder

2. **AI Fixer Bot** (`fixer.yml`)
   - Statik analiz yapar
   - `ai_report.json` oluşturur
   - `auto.patch` üretir (eğer düzeltmeler varsa)
   - PR'a özet yorum yapar

---

## 🔍 Sonuçları Kontrol Et

1. **PR sayfasında:**

   - Inline yorumları kontrol et
   - PR yorumlarında AI Fixer Bot özetini gör

2. **GitHub Actions sekmesinde:**
   - Workflow'ların çalıştığını gör
   - Artifacts'tan `ai-auto-patch` dosyasını indir

---

## 🧹 Test Sonrası Temizlik

Test başarılı olduktan sonra:

```bash
git checkout main
git branch -D test/ai-fixer-bot
git push origin --delete test/ai-fixer-bot
rm test_ai_fixer_change.cpp
```

---

**Hazırsan yukarıdaki linke tıkla ve PR'ı oluştur! 🎯**

