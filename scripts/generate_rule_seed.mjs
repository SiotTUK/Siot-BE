// #14 FE 생활수칙 하드코딩 데이터(Siot-FE)를 BE 시드 마이그레이션과 테스트 대조용 JSON으로 만든다.
// 손으로 옮기지 않는다. FE 문구를 고치면 이 스크립트로 새 마이그레이션을 만든다 (이미 적용된 마이그레이션은 고치지 않는다).
//
// 사용법 (Node 22.6 이상: .ts 를 타입만 지우고 읽는다):
//   node scripts/generate_rule_seed.mjs ../Siot-frontend
// 출력:
//   src/main/resources/db/migration/V2026_11_26_1610__seed_fe_rules.sql
//   src/test/resources/rules/fe-rules.json
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'

const LANGUAGES = ['ko', 'en', 'zh', 'vi']
const CATEGORY_KEYS = ['order', 'waste', 'traffic', 'tax', 'living', 'support'] // FE ruleCategories (피그마 M06 순서)

const feRoot = resolve(process.argv[2] ?? '../Siot-frontend')
const beRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const rulesDir = resolve(feRoot, 'src/features/rules')

const { ruleEntries, ruleFaqs, rulesCheckedOn } = await import(pathToFileURL(resolve(rulesDir, 'content.ts')).href)

// messages.ts 는 '@/...' 별칭을 import 하므로 직접 불러오지 않고, 언어별 `categories: { ... }` 블록만 읽는다
const messages = readFileSync(resolve(rulesDir, 'messages.ts'), 'utf8')
const categoryNames = {}
for (const lang of LANGUAGES) {
  const block = messages.match(new RegExp(`\\n  ${lang}: \\{[\\s\\S]*?categories: \\{([\\s\\S]*?)\\}`))
  if (!block) throw new Error(`categories for ${lang} not found in messages.ts`)
  const names = {}
  for (const [, key, value] of block[1].matchAll(/(\w+):\s*'([^']*)'/g)) names[key] = value
  for (const key of CATEGORY_KEYS) {
    if (!names[key]) throw new Error(`category ${key} (${lang}) missing in messages.ts`)
  }
  categoryNames[lang] = names
}

// ---- 검증: FE 구조가 바뀌었으면 멈춘다 ----
const codes = new Set()
for (const entry of ruleEntries) {
  if (codes.has(entry.code)) throw new Error(`duplicate code ${entry.code}`)
  codes.add(entry.code)
  if (!CATEGORY_KEYS.includes(entry.category)) throw new Error(`unknown category ${entry.category}`)
  for (const lang of LANGUAGES) {
    if (!entry.title[lang] || !entry.summary[lang]) throw new Error(`${entry.code}: missing ${lang} title/summary`)
    entry.points.forEach((p, i) => { if (!p[lang]) throw new Error(`${entry.code}: point ${i} missing ${lang}`) })
  }
  for (const s of entry.sources) if (!/^https?:\/\//.test(s.url)) throw new Error(`${entry.code}: bad url ${s.url}`)
}
for (const faq of ruleFaqs) if (!codes.has(faq.rule)) throw new Error(`FAQ links unknown rule ${faq.rule}`)

// ---- SQL ----
const q = (v) => (v === undefined || v === null ? 'NULL' : `'${String(v).replaceAll("'", "''")}'`)
const arr = (values) => (values.length === 0 ? `'{}'::text[]` : `ARRAY[${values.map(q).join(', ')}]::text[]`)

const out = []
out.push(`-- #14 FE 생활수칙 데이터 시드. scripts/generate_rule_seed.mjs 로 Siot-FE src/features/rules/content.ts·messages.ts 에서 만들었다.`)
out.push(`-- 손으로 고치지 않는다. 항목 ${ruleEntries.length}개, FAQ ${ruleFaqs.length}개, 카테고리 ${CATEGORY_KEYS.length}개, 4개 언어. 공식 페이지 확인일 ${rulesCheckedOn}.`)
out.push(`-- 4개 언어 문구는 사람이 확인했으므로 reviewed = TRUE (자동 번역이 덮어쓰지 않는다).`)
out.push('')
out.push('-- 카테고리: 한국어 이름 = guide.category, FE 키·순서, en/zh/vi 이름')
CATEGORY_KEYS.forEach((key, i) => {
  out.push(`INSERT INTO rule_category (category, category_key, sort_order) VALUES (${q(categoryNames.ko[key])}, ${q(key)}, ${(i + 1) * 10})`)
  out.push(`ON CONFLICT (category) DO UPDATE SET category_key = EXCLUDED.category_key, sort_order = EXCLUDED.sort_order;`)
  for (const lang of ['en', 'zh', 'vi']) {
    out.push(`INSERT INTO rule_category_translation (category, language, name) VALUES (${q(categoryNames.ko[key])}, '${lang}', ${q(categoryNames[lang][key])})`)
    out.push(`ON CONFLICT (category, language) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP;`)
  }
})
out.push('')

const faqCodes = new Set(ruleFaqs.map((f) => f.rule))
const perCategory = {}
for (const entry of ruleEntries) {
  perCategory[entry.category] = (perCategory[entry.category] ?? 0) + 1
  const sortOrder = (CATEGORY_KEYS.indexOf(entry.category) + 1) * 100 + perCategory[entry.category]
  out.push(`-- ${entry.code}`)
  out.push(`INSERT INTO guide (code, kind, item_type, category, sort_order, is_faq, source_url, checked_on)`)
  out.push(`VALUES (${q(entry.code)}, 'RULE', 'INFO', ${q(categoryNames.ko[entry.category])}, ${sortOrder}, ${faqCodes.has(entry.code) ? 'TRUE' : 'FALSE'}, ${q(entry.sources[0]?.url)}, ${q(rulesCheckedOn)})`)
  out.push(`ON CONFLICT (code) DO NOTHING;`)
  for (const lang of LANGUAGES) {
    const f = entry.facts ?? {}
    const points = entry.points.map((p) => p[lang])
    out.push(`INSERT INTO guide_translation (language, guide_id, title, summary, content, points, place, timing, fee, law, penalty, contact, source_name, reviewed)`)
    out.push(`SELECT '${lang}', guide_id, ${q(entry.title[lang])}, ${q(entry.summary[lang])}, ${q(points.join('\n'))}, ${arr(points)},`)
    out.push(`       ${q(f.where?.[lang])}, ${q(f.when?.[lang])}, ${q(f.fee?.[lang])}, ${q(f.law?.[lang])}, ${q(f.penalty?.[lang])}, ${q(f.contact?.[lang])}, ${q(entry.sources[0]?.name)}, TRUE`)
    out.push(`FROM guide WHERE code = ${q(entry.code)} ON CONFLICT (language, guide_id) DO NOTHING;`)
  }
  entry.sources.forEach((s, i) => {
    out.push(`INSERT INTO guide_source (guide_id, sort_order, name, url) SELECT guide_id, ${i + 1}, ${q(s.name)}, ${q(s.url)} FROM guide WHERE code = ${q(entry.code)}`)
    out.push(`ON CONFLICT (guide_id, sort_order) DO NOTHING;`)
  })
  out.push('')
}

out.push('-- FAQ (순서대로)')
ruleFaqs.forEach((faq, i) => {
  const values = LANGUAGES.map((lang) => `('${lang}', ${q(faq.question[lang])}, ${q(faq.answer[lang])})`).join(',\n           ')
  out.push(`WITH faq AS (`)
  out.push(`    INSERT INTO rule_faq (guide_id, sort_order) SELECT guide_id, ${i + 1} FROM guide WHERE code = ${q(faq.rule)} RETURNING faq_id)`)
  out.push(`INSERT INTO rule_faq_translation (faq_id, language, question, answer)`)
  out.push(`SELECT faq.faq_id, t.language, t.question, t.answer FROM faq,`)
  out.push(`    (VALUES ${values}) AS t(language, question, answer);`)
})
out.push('')

const sqlPath = resolve(beRoot, 'src/main/resources/db/migration/V2026_11_26_1610__seed_fe_rules.sql')
writeFileSync(sqlPath, out.join('\n'), 'utf8')

// ---- 테스트 대조용 JSON (FE 원본 그대로 + 카테고리 이름) ----
const jsonPath = resolve(beRoot, 'src/test/resources/rules/fe-rules.json')
mkdirSync(dirname(jsonPath), { recursive: true })
writeFileSync(jsonPath, JSON.stringify({
  checkedOn: rulesCheckedOn,
  categories: CATEGORY_KEYS.map((key) => ({ key, names: Object.fromEntries(LANGUAGES.map((l) => [l, categoryNames[l][key]])) })),
  rules: ruleEntries,
  faqs: ruleFaqs,
}, null, 2) + '\n', 'utf8')

console.log(`rules=${ruleEntries.length} faqs=${ruleFaqs.length} categories=${CATEGORY_KEYS.length}`)
console.log(sqlPath)
console.log(jsonPath)
