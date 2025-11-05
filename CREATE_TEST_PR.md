# AI Fixer Bot Test PR Oluşturma

## Hızlı Yöntem

Aşağıdaki linke tıklayarak PR oluştur:

🔗 **https://github.com/halibiram/SimpleXray/compare/main...test/ai-fixer-bot**

## PR Detayları

**Başlık:**

```
test: AI Fixer Bot test
```

**Açıklama:**

```
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

## Alternatif: GitHub CLI ile

Eğer GitHub CLI'ye authentication yaptıysan:

```bash
gh auth login
gh pr create --title "test: AI Fixer Bot test" --body "Test PR for AI Fixer Bot" --base main --head test/ai-fixer-bot
```

## Workflow'ların Çalışması

PR oluşturulduğunda otomatik olarak şu workflow'lar çalışacak:

1. **AI Inline Code Fixer** (`inline-fixer.yml`)

   - PR'da inline yorumlar yapar
   - Her değişiklik satırını analiz eder

2. **AI Fixer Bot** (`fixer.yml`)
   - Statik analiz yapar
   - `ai_report.json` oluşturur
   - `auto.patch` üretir (eğer düzeltmeler varsa)
   - PR'a özet yorum yapar

## GitHub Secrets Kontrolü

PR oluşturmadan önce:

- Repository Settings → Secrets and variables → Actions
- `OPENAI_API_KEY` secret'ının eklendiğinden emin ol

## Test Sonrası

Test başarılı olduktan sonra:

```bash
git checkout main
git branch -D test/ai-fixer-bot
git push origin --delete test/ai-fixer-bot
rm test_ai_fixer_change.cpp
```

