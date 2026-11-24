# Blockchain v0.3

Mokomoji vieno proceso blokų grandinė Java kalba: savo maišos funkcija, transakcijų fondas, Merkle medis,
trijų etapų transakcijų ir blokų validacija, darbo įrodymas (PoW) ir penkių kandidatų konkurencija dėl kiekvieno
bloko. Išorinių bibliotekų nėra.

**v0.3 naujovės** (patobulinta su DI įrankiu; v0.1 ir v0.2 reikalavimai išlaikyti, rezultatai identiški):
* kasimas ~9× greitesnis: `Hash.toHex` be `String.format` ir midstate (`PrefixHasher`) kasimo cikle;
* netinkamo formato blokai ir `null` reikšmės atmetamos su priežastimi, o ne `NullPointerException`;
* bendras 5 skyriaus teisingumo rinkinys, paleistas ir v0.2, ir v0.3; 53 testai;
* palyginimas, išvados ir DI žurnalas – skyrius [„v0.3: patobulinimas su DI“](#v03-patobulinimas-su-di),
  [`docs/DI_ZURNALAS.md`](docs/DI_ZURNALAS.md).

**v0.2 naujovės** (v0.1 eiga išlaikyta):
* `tx_root` – Merkle medžio šaknis (bloko formato versija 2);
* validacija išskirta į atskiras procedūras ir tris etapus: priėmimas į fondą, kandidato sudarymas, bloko priėmimas;
* kiekvienam aukščiui – 5 kandidatai su ribotu bandymų / laiko biudžetu, dokumentuota laimėtojo taisyklė;
* 40 testų, tarp jų Merkle, neigiami validavimo ir kandidatų testai.

## Paleidimas

Reikia JDK 17 ar naujesnio (tikrinta su OpenJDK 21).

Linux / macOS / Git Bash terminale:

```bash
./run.sh
```

```bash
./test.sh
```

Windows PowerShell arba cmd (`./run.sh` čia neveikia – Windows `.sh` failą atidaro atskirame lange):

```bash
.\run.bat
```

```bash
.\test.bat
```

Parinktys perduodamos taip pat, pvz. `.\run.bat --seed 7 --verbose`. Rankiniu būdu:

```bash
javac -encoding UTF-8 -d out/classes src/bc/*.java src/bc/core/*.java src/bc/hash/*.java src/bc/model/*.java src/bc/sim/*.java
```

```bash
java -cp out/classes bc.App
```

`.gitattributes` užtikrina, kad `.sh` skriptai visada būtų su LF eilučių pabaigomis (su CRLF bash jų nepaleidžia).

Parinktys (`java -cp out/classes bc.App --help`):

| Parinktis | Numatyta | Reikšmė |
|---|---|---|
| `--users N` | 1000 | vartotojų skaičius |
| `--txs N` | 10000 | sugeneruojamų transakcijų skaičius |
| `--block-size N` | 100 | daugiausia transakcijų bloke (grandinė tikrina šią ribą) |
| `--difficulty N` | 3 | kiek pradinių `0` turi turėti bloko hash (hex) |
| `--seed N` | 20261029 | generavimo seed |
| `--max-amount N` | 50000 | didžiausia generuojama mokėjimo suma |
| `--invalid-rate X` | 0.01 | tyčia sugadintų transakcijų dalis (atmetimo logikai parodyti) |
| `--out DIR` | `out` | kur rašyti `initial_state.csv` ir `report.txt` |
| `--fixed-time` | išjungta | `timestamp` = bloko aukštis, todėl visa grandinė atkuriama |
| `--candidates N` | 5 | kandidatų kiekvienam aukščiui |
| `--budget N` | 2000 | pradinis bandymų biudžetas kandidatui per raundą |
| `--budget-ms N` | 5000 | laiko biudžetas kandidatui per raundą (ms) |
| `--max-rounds N` | 10 | kiek raundų (biudžeto dvigubinimų) daugiausia, prieš sustabdant paiešką |
| `--verbose` | išjungta | kiekvienam aukščiui spausdinti kiekvieno kandidato eilutę |

Programa sukuria failus:
* `out/initial_state.csv` – pradinė būsena (vardas, viešasis raktas, pradinis balansas), įrašoma **prieš**
  taikant bet kurią transakciją;
* `out/report.txt` – kiekviena atmesta ir kiekviena fonde likusi transakcija su priežastimi, visų blokų sąrašas.

## Eiga (viename procese)

1. Su `seed` sukuriami vartotojai: `name = userNNNN`, `public_key = H("user|i|<atsitiktinis long>")`
   (64 hex; tai tik imituotas identifikatorius, rakto nuosavybės neįrodo), pradinis balansas 100–1 000 000.
   Iš jų sudaroma pradinė būsena; jos kopija išsaugoma (`Blockchain.initialBalances()`, `initial_state.csv`).
2. Sugeneruojamos transakcijos (siuntėjas ≠ gavėjas, suma 1..`max-amount`, `sequence` = siuntėjo transakcijų
   skaitliukas). Apie 1 % tyčia sugadinamos: savęs pervedimas, nežinomas gavėjas, suma 0, ID neatitinka turinio,
   dublikatas.
3. **1 etapas** – kiekviena transakcija pateikiama į fondą (žr. „Validacija“).
4. Kiekvienam aukščiui, kol fonde yra įvykdomų transakcijų:
   * **2 etapas** – sudaromi 5 kandidatai (žr. „Penki kandidatai“);
   * kandidatai kasami ribotu biudžetu, pagal taisyklę parenkamas laimėtojas;
   * **3 etapas** – laimėtojas iš naujo pilnai patikrinamas ir vienu veiksmu prijungiamas; jo transakcijos
     pašalinamos iš fondo. Nelaimėjusių kandidatų poveikio nėra, kitam aukščiui kandidatai sudaromi iš naujo.
5. Sustojama, kai nė vienas kandidatas nebegauna transakcijų (nėra pažangos) arba kai per `max-rounds` raundų
   niekas neranda sprendimo. Išspausdinama suvestinė; `report.txt` kiekvienai likusiai transakcijai nurodo,
   kiek siuntėjas turi ir kiek reikia.

## Modelis

* **Account modelis**: `State` = `public_key → balansas` (`long`). Transakcija tik perkelia `amount` nuo
  siuntėjo gavėjui, todėl bendra suma nekinta. Invariantai: balansai visada `≥ 0`, bendra lėšų suma lygi
  pradinei, kiekviena sugeneruota transakcija yra arba įtraukta, arba atmesta, arba likusi fonde.
* **Transakcija**: `sender`, `receiver` (64 hex), `amount`, `sequence`, `id`.
* **Bloko antraštė** (6 laukai): `prev_hash`, `timestamp` (Unix sekundės), `version` (=2), `tx_root`
  (Merkle šaknis), `nonce`, `difficulty`. Pirmojo bloko (aukštis 0) `prev_hash` = 64 nuliai.
* **Blokas** = antraštė + 1..100 transakcijų. Bloko hash nesaugomas – jis visada skaičiuojamas iš antraštės.

| Klasė | Paskirtis |
|---|---|
| `bc.hash.Hash`, `HashUtil` | savo maišos funkcija, UTF-8 maiša, hex ↔ baitai |
| `bc.model.*` | `User`, `Transaction`, `BlockHeader`, `Block` |
| `bc.core.State` | balansai, `check`/`apply` (balansas, perpildymas), suma |
| `bc.core.Validation` | atskiros transakcijos taisyklių procedūros, bendros visiems etapams |
| `bc.core.Validator` | 1 etapas – priėmimas į fondą |
| `bc.core.CandidateBuilder` | 2 etapas – atranka į kandidatą laikinoje būsenoje |
| `bc.core.Blockchain` | 3 etapas – `checkHeader`, `checkBody`, `applyTransactions`, atominis `addBlock` |
| `bc.core.BlockError`, `TxError` | bloko ir transakcijos atmetimo priežastys |
| `bc.core.Merkle` | Merkle medis (`tx_root`) |
| `bc.core.Pow` | PoW paieška su biudžetu (v0.3: midstate) ir patikra |
| `bc.hash.PrefixHasher` | v0.3: maiša su iš anksto apskaičiuota pastovaus prefikso būsena |
| `bc.core.Candidate`, `Competition` | kandidatai, jų kasimas raundais, laimėtojo parinkimas |
| `bc.core.TxPool` | laukiančios ir atmestos transakcijos |
| `bc.core.Miner` | vieno kasėjo režimas (v0.1 API) |
| `bc.sim.*` | parametrai, deterministinis generatorius, simuliacija ir suvestinė |

## Kodavimas prieš maišą

Kūrimui ir patikrai naudojamos tos pačios funkcijos (`Transaction.serialize`, `BlockHeader.serialize`, `Merkle.root`).

* **Transakcija**: `sender|receiver|amount|sequence`, koduojama UTF-8, be eilutės pabaigos.
  `transaction_id = H(UTF-8(serializacija))`; pats ID į įvestį neįtraukiamas.
  Pvz. `a12f|b34e|100|5` → `7437bf5156dfb2e4b65440ad4c2f6b19e6fdd1670e14adfbe70883337ef5d363`
  (`a12f`, `b34e` – sutrumpinti mokomieji identifikatoriai; programoje jie 64 hex).
* **Bloko antraštė**: `prev_hash|timestamp|version|tx_root|nonce|difficulty`, UTF-8, be eilutės pabaigos.
  Bloko hash = `H(UTF-8(serializacija))`, o ne objekto adreso ar `toString()` maiša.
* Skirtukas `|` laukų reikšmėse negali pasitaikyti: hash laukai turi tik `0-9a-f` (tikrinama `HashUtil.isHash`),
  skaičiai rašomi `Long.toString` – dešimtainiai, be tarpų, pliuso ir nereikalingų pradinių nulių. Todėl
  `1|23` ir `12|3` duoda skirtingus baitus (žr. testą). Laisvo teksto laukų nėra.

## Merkle medis (`tx_root`, nuo v0.2)

* **Lapai** – transakcijų ID jų tvarka bloke. Prieš jungimą kiekvienas 64 hex ID dekoduojamas į 32 baitus.
* **Vidinis mazgas** = `H(kairiojo vaiko 32 B || dešiniojo vaiko 32 B)` – jungiami baitai, ne hex tekstas;
  `H` – ta pati savo maiša.
* **Neporinis mazgas** lygyje poruojamas su savo kopija: `H(x || x)`.
* **Vienas lapas** – jis pats yra šaknis (nehešuojamas dar kartą).
* **Tuščias sąrašas** – `EMPTY_ROOT` = 64 nuliai. Tai apibrėžta tik genesis atvejui; grandinė tuščių blokų
  nepriima (`BlockError.EMPTY`), o pirmasis blokas visada turi transakcijų.

Pavyzdys su 5 transakcijomis (`a..e` – ID):

```
lygis 0:  a      b      c      d      e
lygis 1:  H(a|b)        H(c|d)        H(e|e)
lygis 2:  H(ab|cd)                    H(ee|ee)
šaknis:   H(abcd|eeee)
```

Testai (`MerkleTests`) šaknį 1, 2, 3 ir 5 transakcijoms skaičiuoja nepriklausomai ir lygina su `Merkle.root`;
tikrina, kad pakeitus bet kurios transakcijos turinį ir perskaičiavus jos ID, taip pat sukeitus bet kurias dvi
skirtingas transakcijas, šaknis pasikeičia.

**Dokumentuota savybė (ne maišos kolizija).** Dėl neporinio mazgo kopijavimo `[a,b,c]` ir `[a,b,c,c]` turi tą
pačią šaknį – tai žinomas ir Bitcoin medžio atvejis (CVE-2012-2459). Antrasis sąrašas turi pasikartojantį ID, todėl
grandinė tokį bloką atmeta (`DUPLICATE_IN_BLOCK`); testas tai patikrina.

**Maišos kolizijos.** Testas sumaišo 200 000 skirtingų įvesčių – kolizijos nerasta. Pilna 256 bitų kolizijos paieška
nevykdyta; jei kolizija būtų rasta, testas ją praneštų su abiem įvestimis.

## Validacija (trys etapai)

Visi etapai naudoja tas pačias `Validation` procedūras. Vienkartinė patikra prieš fondą negarantuoja, kad
transakciją bus galima įvykdyti konkrečiame bloke, todėl balansas tikrinamas tik 2 ir 3 etapuose.

| Taisyklė | `TxError` | 1. į fondą | 2. kandidatas | 3. blokas |
|---|---|---|---|---|
| laukų formatas (64 hex, `sequence ≥ 0`) | `BAD_FORMAT` | ✔ | ✔ | ✔ |
| teigiama sveikoji suma | `NON_POSITIVE_AMOUNT` | ✔ | ✔ | ✔ |
| žinomi dalyviai, siuntėjas ≠ gavėjas | `UNKNOWN_*`, `SELF_TRANSFER` | ✔ | ✔ | ✔ |
| perskaičiuotas `transaction_id` | `BAD_ID` | ✔ | ✔ | ✔ |
| ID nėra fonde | `DUPLICATE_IN_POOL` | ✔ | | |
| ID nėra patvirtintas grandinėje | `ALREADY_CONFIRMED` | ✔ | ✔ | ✔ |
| ID nesikartoja bloke | `DUPLICATE_IN_BLOCK` | | ✔ | ✔ |
| balansas laikinoje būsenoje | `INSUFFICIENT_BALANCE` | | ✔ | ✔ |
| gavėjo balanso perpildymas | `OVERFLOW` | | ✔ | ✔ |

**1. Prieš priimant į fondą** (`Validator.admission`). Balansas netikrinamas: patekimas į fondą lėšų nerezervuoja.

**2. Atrenkant į kandidatą** (`CandidateBuilder.select`). Patvirtinti balansai nukopijuojami į laikiną būseną;
transakcijos tikrinamos ir taikomos šiai kopijai numatoma tvarka bloke, atrenkama iki 100. Kiekvienas kandidatas
turi savo kopiją. Pavyzdys (testas `2 etapas: A=100, T1 70 + T2 60`):

| Žingsnis | Kandidato laikina būsena |
|---|---|
| Tikriname T1: 70 ≤ 100 | T1 įtraukiama, laikinas A = 30 |
| Tikriname T2: 60 > 30 | T2 į šį kandidatą neįtraukiama (lieka fonde) |
| Jei pirmiau T2 | A = 40, tada T1 (70) netinka |

Tikrieji balansai atrankos metu nekinta (testas tai tikrina).

**3. Prieš priimant iškastą bloką** (`Blockchain.addBlock`), atskiros procedūros:
* `checkHeader`: palaikoma versija (2), `prev_hash` = paskutinio bloko hash, `difficulty` sutampa su grandinės
  (mažesnis → `LOWERED_DIFFICULTY`, kitas → `BAD_DIFFICULTY`), PoW tikrinamas **pagal grandinės** sudėtingumą,
  maišą perskaičiuojant – kandidatas negali pats sumažinti reikalavimo;
* `checkBody`: 1..100 transakcijų (`EMPTY`, `TOO_MANY_TX`), formatas, Merkle šaknis (`BAD_TX_ROOT`);
* `applyTransactions`: visos transakcijos jų tvarka vienoje patvirtintos būsenos kopijoje (`BAD_TX` + priežastis),
  bendra suma ir neneigiami balansai.

Tik visam blokui praėjus patikrą, vienu veiksmu atnaujinami blokai, balansai, patvirtintų ID aibė ir fondas.
Netinkamas blokas nekeičia nieko – testas tikrina balansus, aukštį, paskutinį hash, patvirtintus ID ir fondą.

**Atmesti ar atidėti?** Neteisingas ID, ne teigiama suma, nežinomas dalyvis, savęs pervedimas, dublikatas,
perpildymas – atmetama su priežastimi. Nepakankamas balansas – **atidedama**: transakcija lieka fonde ir
tikrinama kiekviename naujame kandidate (siuntėjas gali gauti lėšų). Jei visas atrankos ciklas nesukuria
pažangos, sustojama ir pateikiama likusių suvestinė.

**`sequence`.** Naudojamas siuntėjo transakcijų skaitliukas iš generatoriaus; kartu su siuntėju jis unikalus ir
užtikrina skirtingus ID. Griežto eiliškumo (laukti dar neatėjusio numerio) neįgyvendinome – užduotyje to
nereikalaujama. Pakartotinis tos pačios transakcijos pateikimas blokuojamas per ID (`ALREADY_CONFIRMED`).

**ID ≠ parašas.** Teisingas ID įrodo tik tai, kad turinys nepakeistas po ID apskaičiavimo. Bet kas, žinantis A
identifikatorių, gali sukurti `A -> B` su teisingu ID – testas `ID atitikimas ... neatstoja paraso` tai parodo.
Siuntėjo leidimą įrodytų tik skaitmeninis parašas (v0.2 jo nėra).

## Penki kandidatai kitam blokui

**Sudarymas.** Tam pačiam aukščiui sudaromi 5 kandidatai: visi remiasi tuo pačiu `prev_hash`, ta pačia patvirtinta
būsena ir tuo pačiu `timestamp`. Kiekvienas gauna savo atsitiktinę (nuo `seed` priklausomą) fondo tvarką ir savo
laikiną būsenos kopiją, todėl jų turinys ir `tx_root` skiriasi. Kandidatų transakcijos gali persidengti, viename
kandidate ID nesikartoja. Kiekviename iki 100 transakcijų.

**Tikrinimo tvarka ir biudžetas.** Kasama nuosekliai, raundais. Raunde kandidatai tikrinami eilės tvarka
K1, K2, …, K5. Kiekvienas tęsia nuo savo pirmo dar netikrinto `nonce` ir tikrina, kol randa sprendimą arba
išnaudoja raundo biudžetą: **2000 bandymų arba 5 s – kas pirmiau**. Jei po raundo niekas nerado, biudžetas
dvigubinamas (2000, 4000, 8000, …), paieška tęsiama nuo netikrintų `nonce`, sudėtingumas nekeičiamas.

**Bendra riba.** Daugiausia 10 raundų (iki 2 046 000 bandymų kandidatui). Jei sprendimo nerasta, paieška aiškiai
sustabdoma: išspausdinama priežastis, grandinė nekeičiama, likusios transakcijos lieka fonde. Pvz.
`./run.sh --budget 10 --max-rounds 1` sustoja jau pirmame aukštyje.

**Laimėtojo taisyklė.** Iš raunde sprendimą radusių laimi tas, kuriam prireikė **mažiausiai bandymų** (bendrai per
visus raundus); lygiųjų atveju – mažesnis numeris. Tai atitinka „kas pirmas rastų, jei visi kastų lygiagrečiai
vienodu greičiu“, nors kasama nuosekliai. Laimėtojas iš naujo pilnai patikrinamas (`Blockchain.addBlock`), įtraukiamos
tik jo transakcijos. Nelaimėjusių kandidatų blokai nebeprisijungia (jų `prev_hash` jau pasenęs), jų transakcijos,
nepatekusios į laimėtoją, lieka fonde, ir kitam aukščiui kandidatai sudaromi iš naujo.

### Kodėl tai dar nėra P2P konsensusas

* **Vienas procesas, vienas validatorius.** Visi „kasėjai“ yra objektai toje pačioje atmintyje, mato tą patį fondą
  ir tą pačią patvirtintą būseną. Tikrame tinkle kiekvienas mazgas turi savo fondo ir grandinės vaizdą.
* **Nėra tinklo.** Nėra blokų ir transakcijų sklaidos, vėlavimų, prarastų žinučių – todėl negali atsirasti dviejų
  vienu metu rastų blokų ir **šakų**.
* **Nėra šakų pasirinkimo taisyklės.** Nėra „daugiausia darbo turinčios grandinės“ taisyklės ir persitvarkymų
  (reorg); laimėtoją parenka viena funkcija pagal mūsų taisyklę, o ne mazgai nepriklausomai.
* **Konkurencija simuliuojama nuosekliai.** „Kas pirmas“ nustatoma pagal bandymų skaičių, ne pagal tikrą laiką ir
  skirtingą kasėjų skaičiavimo galią.
* **Nėra priešininkų ir parašų.** Visi dalyviai sąžiningi, transakcijos nepasirašomos, sudėtingumas nekinta.

Tai parodo, *kaip* kandidatai konkuruoja ir kodėl įtraukiamas tik vieno jų turinys, bet ne tai, *kaip* nepriklausomi
mazgai be centrinio koordinatoriaus susitaria dėl vienos istorijos.

## Maišos funkcija

Versija: **KGHash v2 (8 raundai, message schedule, K konstantos, StrictMath)** (`Hash.VERSION`). v0.2 jos nekeitė.

* Išvestis 256 bitai = 8 × 32 bitų žodžiai, kiekvienas `%08x` → visada 64 mažosios hex raidės, pradiniai nuliai
  išlaikomi.
* Užpildymas: `0x80`, nuliai, 64 bitų įvesties ilgis bitais; blokai po 32 baitus.
* Kiekvienam blokui 8 žodžiai išplečiami iki 64 (message schedule), 8 raundai × 8 žingsniai su Ch/Maj funkcijomis,
  Σ/σ rotacijomis ir konstantomis K (pirmų 64 pirminių skaičių kubinių šaknų trupmeninės dalys), pabaigoje
  pridedama ankstesnė būsena.

Pakeitimų žurnalas:
* v1 → v2: raundų skaičius 4 → 8, pridėtas message schedule, K konstantos ir netiesinės Ch/Maj funkcijos.
* v0.1 leidime: `Math.cbrt` pakeista į `StrictMath.cbrt`, kad K konstantos būtų garantuotai vienodos visose
  platformose. Išvestis nepasikeitė – patikrinta testų vektoriais, formato ir determinizmo testai pakartoti.

Testų vektoriai:

| Įvestis (UTF-8) | Maiša |
|---|---|
| `""` | `e209e47debe9cdafc922ad38f2bdb0105fa7a00a7130f088c4b797095a2116f2` |
| `abc` | `699b56920847030b53e5f67a423b8add976670faef02625dd72a9f9e8785c111` |
| `a12f\|b34e\|100\|5` | `7437bf5156dfb2e4b65440ad4c2f6b19e6fdd1670e14adfbe70883337ef5d363` |
| antraštė `0…0\|1700000000\|1\|<H("")>\|42\|3` | `2da8fbc2b786bc949f3ab6f00ea6367dd0a4dfb9d671d2bc07dc40f0703f18c9` |

## Darbo įrodymas

* Sąlyga: bloko hash hex užrašas prasideda bent `difficulty` nulių (pagrindinis bandymas `difficulty = 3`,
  t. y. `000…`, vidutiniškai ~4096 bandymai).
* `nonce` didinamas nuosekliai nuo 0, kiti antraštės laukai vieno kandidato paieškoje fiksuoti.
* Gauta maiša niekaip nekeičiama. PoW visada tikrinamas **perskaičiuojant** maišą iš antraštės laukų ir pagal
  grandinės nustatytą sudėtingumą.
* Tai mokomasis sudėtingumo apibrėžimas; Bitcoin `target` ir sudėtingumo perskaičiavimas nerealizuoti.
* v0.3: kasant pastovi antraštės dalis `prev_hash|timestamp|version|tx_root|` sumaišoma vieną kartą (midstate),
  kiekvienam bandymui – tik `nonce|difficulty`. Tikrinimas (`Pow.meets`, `Blockchain.checkHeader`) liko toks pat –
  pilna maiša iš visų šešių laukų; rastas sprendimas papildomai patikrinamas pilna maiša.

## Generavimo seed ir atkuriamumas

Numatytas seed: **20261029**. `java.util.Random` algoritmas apibrėžtas Java specifikacijoje, todėl tas pats seed
visur duoda tuos pačius vartotojus, transakcijas, jų ID ir kandidatų tvarkas. Konsolėje rodomas `tx_set_fp` – visų
sugeneruotų transakcijų ID maiša; su numatytais parametrais ji visada
`9b19c7458f52b60f2676146510e28536a2c7489c229ec4a6d02cab72ea2a6ca9` (v0.1, v0.2 ir v0.3 sutampa).

Bloko hash priklauso ir nuo `timestamp`, todėl be `--fixed-time` blokai ir laimėtojai tarp paleidimų skiriasi.
Su `--fixed-time` visa grandinė atkuriama: su numatytais parametrais paskutinio bloko hash visada
`0001bb1ec03a3d2b3bb8da33b4fe8fd8e31d56363160ef95f85cd7f1b2991b2a` (98 blokai, 9717 įtrauktų tx; v0.2 ir v0.3 sutampa).
Laiko biudžetas (5 s) su numatytais parametrais nepasiekiamas, todėl rezultato nekeičia.

## Išvesties pavyzdys (`./run.sh --verbose`, numatyti parametrai)

Pavyzdys užfiksuotas su v0.2; v0.3 išvestis tokia pati, skiriasi tik antraštės eilutė ir laikai (žr. palyginimą).

```
=== Blockchain v0.2 ===
hash:        KGHash v2 (8 raundai, message schedule, K konstantos, StrictMath)
seed:        20261029
parametrai:  users=1000 txs=10000 block_size=100 difficulty=3 max_amount=50000 invalid_rate=0.01
kandidatai:  5 aukstiui, biudzetas 2000 bandymu arba 5000 ms kandidatui per raunda, nepavykus x2, daugiausia 10 raundu
tx_set_fp:   9b19c7458f52b60f2676146510e28536a2c7489c229ec4a6d02cab72ea2a6ca9  (visu sugeneruotu tx ID maisa)
fondas:      priimta 9900, atmesta priimant 100

Blokas #0 | tx=100 | laimetojas K2
  prev_hash:  0000000000000000000000000000000000000000000000000000000000000000
  tx_root:    cc2bb4d3729d8aa92266fb92e2489369692b65dd7d3c53349b12a5e4e4287d58  (Merkle)
  hash:       000526770ca8355f63c5412e85ba020e3373639aee814812892521d425f9ad84
  difficulty=3 nonce=282 bandymai=283 laikas=3.5 ms timestamp=1795440336
  konkurencija: 5 kandidatai, raundai=1, biudzetas=2000, visi bandymai=7190, laikas=96.6 ms
    K1 tx=100 tx_root=f06d5f32e915a560... bandymai=1666   laikas=  29.1 ms rado nonce=1665
    K2 tx=100 tx_root=cc2bb4d3729d8aa9... bandymai=283    laikas=   3.5 ms LAIMEJO nonce=282
    K3 tx=100 tx_root=f6ada547f5ee2044... bandymai=1241   laikas=  23.8 ms rado nonce=1240
    K4 tx=100 tx_root=c964e799562e26d9... bandymai=2000   laikas=  20.7 ms nerado
    K5 tx=100 tx_root=77dc503468946102... bandymai=2000   laikas=  19.5 ms nerado

...

Blokas #8 | tx=100 | laimetojas K2
  prev_hash:  000772a218d6e1c38dd6287db7fc04a540cc89a2877d8c2ba7dfe4e2640ce2ae
  tx_root:    eedf82484cbcc75ff4e01e60c54aa21784ccf62411aa30ac22289c9a3b5da04c  (Merkle)
  hash:       000c3b41496f092a10b955503e6558ce17c90558daacc0318f0c0924c0241e0b
  difficulty=3 nonce=2223 bandymai=2224 laikas=11.3 ms timestamp=1795440337
  konkurencija: 5 kandidatai, raundai=2, biudzetas=4000, visi bandymai=14465, laikas=60.3 ms
    K1 tx=100 tx_root=9289868961ec28e6... bandymai=2853   laikas=  11.2 ms rado nonce=2852
    K2 tx=100 tx_root=eedf82484cbcc75f... bandymai=2224   laikas=  11.3 ms LAIMEJO nonce=2223
    K3 tx=100 tx_root=805ae6297b98012b... bandymai=2478   laikas=   9.5 ms rado nonce=2477
    K4 tx=100 tx_root=3fb454aa6c0e0ddd... bandymai=3333   laikas=  13.4 ms rado nonce=3332
    K5 tx=100 tx_root=548d092f1d9972d9... bandymai=3577   laikas=  14.9 ms rado nonce=3576

...

=== Suvestine ===
sustojimo priezastis:  fonde nebeliko ivykdomu transakciju
vartotojai:            1000
transakcijos (sugen.): 10000
priimti blokai:        98
itrauktos tx:          9720
atmestos tx:           100
    UNKNOWN_RECEIVER       20 (nezinomas gavejas)
    SELF_TRANSFER          19 (siuntejas sutampa su gaveju)
    NON_POSITIVE_AMOUNT    25 (suma ne teigiama)
    BAD_ID                 22 (transaction_id neatitinka turinio)
    DUPLICATE_IN_POOL      14 (ID jau yra fonde)
likusios fonde tx:     180
    180 - INSUFFICIENT_BALANCE (nepakankamas balansas, galutineje busenoje)
pradine lesu suma:     481767596
galutine lesu suma:    481767596  (sutampa)
maziausias balansas:   325
paskutinio bloko hash: 00026a628053f61a754734109feb1d21c4b4cb47a666950ef93ce5953f7495f5
konkursu (aukstiu):    98, is ju su padidintu biudzetu: 7
bandymai is viso:      848265 (laimetoju 67261, nelaimejusiu 781004)
kasimo laikas:         3.940 s
kandidatas  sudaryta  laimejo  bandymai   laikas
  K1              98       14    176540   0.856 s
  K2              98       20    170447   0.785 s
  K3              98       22    168882   0.790 s
  K4              98       20    164274   0.748 s
  K5              98       22    168122   0.762 s
bendras laikas:        5.309 s
patikra: itrauktos+atmestos+likusios = 10000 = sugeneruota 10000 (OK)
pilnas atmestu/likusiu tx paaiskinimas: out/report.txt
pradine busena: out/initial_state.csv
```

Bloke #8 su pradiniu 2000 bandymų biudžetu niekas nerado sprendimo, todėl biudžetas padidintas iki 4000; kandidatų
`bandymai` yra bendri per abu raundus (pvz. K5: 2000 + 1577). `biudzetas` rodo paskutinio raundo biudžetą.
Be `--verbose` kandidatų eilutės nespausdinamos – lieka bloko ir `konkurencija` eilutės bei galutinė lentelė.

## Testai (`./test.sh`)

v0.3: 53 testai šešiose grupėse. Žemiau – 40 v0.2 testų (jų failai v0.3 nepakeisti); papildomai
6 v0.3 lygiavertiškumo testai (`V03Tests`) ir 7 bendro rinkinio testai (`CorrectnessSuite`, žr. v0.3 skyrių).

v0.2 testų grupės (`test/bc/`):

```
--- v0.1: maisa, serializacija, PoW, balansai ---
[OK]   maisos formatas: 64 mazosios hex, pradiniai nuliai islaikomi
[OK]   maisos determinizmas ir uzfiksuoti vektoriai
[OK]   serializacija sender|receiver|amount|sequence
[OK]   tas pats seed -> tos pacios tx maisos
[OK]   fiksuota antraste -> tas pats bloko hash
[OK]   PoW tikrinimas perskaiciuoja maisa
[OK]   mazas rankinis atvejis (3 vartotojai, 6 mokejimai)
[OK]   negalima isleisti daugiau nei turi keliais mokejimais viename bloke
[OK]   dubliuotos tx atmetamos (fondas, blokas, jau patvirtintos)
[OK]   netinkami blokai atmetami (prev_hash, tx_root, difficulty, tuscias)
[OK]   visa simuliacija: invariantai ir atkuriamumas su --fixed-time

--- v0.2: Merkle medis ---
[OK]   tuscias sarasas -> EMPTY_ROOT (64 nuliai, tik genesis)
[OK]   1 tx: saknis = pats ID
[OK]   2 tx: H(a||b) is baitu, ne is hex teksto
[OK]   3 tx: neporinis poruojamas su kopija
[OK]   5 tx: lygiai 5-3-2-1
[OK]   pakeitus tx turini ir perskaiciavus ID saknis pasikeicia (1,2,3,5)
[OK]   sukeitus skirtingu tx tvarka saknis pasikeicia (2,3,5)
[OK]   zinoma struktura: [a,b,c] ir [a,b,c,c] ta pati saknis -> dublikatas blokuojamas
[OK]   200 000 skirtingu ivesciu - maisos kolizijos nerasta

--- v0.2: transakciju ir bloko validacija (3 etapai) ---
[OK]   1 etapas: netinkamos tx neprileidziamos i fonda (kiekviena taisykle atskirai)
[OK]   1 etapas: patekimas i fonda lesu nerezervuoja
[OK]   2 etapas: A=100, T1 70 + T2 60 -> i kandidata tik viena (abi tvarkos)
[OK]   2 etapas: du mokejimai po 70 is 100 negali abu patekti
[OK]   2 etapas: kandidato atranka nekeicia tikruju balansu
[OK]   balanso perpildymas aptinkamas
[OK]   3 etapas: bloko antrastes klaidos (versija, prev_hash, sudetingumas, PoW)
[OK]   3 etapas: kandidatas negali pats sumazinti sudetingumo
[OK]   3 etapas: turinio klaidos (tuscias, per daug tx, tx_root)
[OK]   3 etapas: blogos tx bloke (balansas, dalyvis, ID, dublikatas, patvirtinta, suma)
[OK]   3 etapas: tx tikrinamos ju tvarka bloke vienoje laikinoje busenoje
[OK]   atmestas blokas nekeicia grandines, balansu, patvirtintu ID ir fondo
[OK]   priimtas blokas vienu veiksmu atnaujina grandine, balansus ir fonda
[OK]   ID atitikimas tikrina vientisuma, bet neatstoja paraso

--- v0.2: penki kandidatai ---
[OK]   5 kandidatai: tas pats prev_hash ir busena, <=100 tx, be pasikartojimu viduje
[OK]   kandidatai skiriasi, bet gali persidengti; tikroji busena nepakinta
[OK]   padidinus biudzeta paieska tesiama nuo netikrintu nonce, sudetingumas nemazinamas
[OK]   laimetojas = maziausiai bandymu is radusiu; perpatikrinamas ir prijungiamas
[OK]   nelaimejusieji neturi poveikio; ju blokas po laimetojo atmetamas
[OK]   bendra riba: per maxRounds nerasta -> aiskus sustojimas, niekas nepakeista

Rezultatas: 40 praejo, 0 nepavyko
```

Testų patikimumas patikrintas mutacijomis: neporinį Merkle mazgą „perkėlus aukštyn“ vietoj kopijavimo, PoW tikrinant
pagal antraštės (ne grandinės) sudėtingumą arba kiekviename raunde pradedant `nonce` nuo 0, atitinkami testai krenta.

Rankomis patikrinamas atvejis (v0.1, bloko dydis 2, `difficulty = 2`, fondo tvarka):

| | A | B | C | Pastaba |
|---|---|---|---|---|
| Pradžia | 100 | 50 | 0 | suma 150 |
| Blokas 0: A→B 30, A→C 50 | 20 | 80 | 50 | A→B 60 nebetelpa (blokas pilnas) |
| Blokas 1: B→C 80, C→A 10 | 30 | 0 | 120 | A→B 60 praleista: 20 < 60 |
| Blokas 2: C→A 15 (nepilnas) | 45 | 0 | 105 | suma 150 |
| Lieka fonde: A→B 60 | | | | 45 < 60 → `INSUFFICIENT_BALANCE` |

## v0.3: patobulinimas su DI

**Įrankis:** Claude Code (Anthropic), modelis `claude-opus-5-5`, 2026-11-24. Užklausos, pasiūlymai, patikros
ir atmetimo priežastys – [`docs/DI_ZURNALAS.md`](docs/DI_ZURNALAS.md). **Atskaita** – užfiksuota v0.2
(žyma `0.2`, commit `1d9999d`). Ji nėra „be DI“: v0.1 dalis ir visa v0.2 realizacija taip pat sukurtos su Claude Code
(atskleista žurnale).

**Kryptys:** kasimo ciklo efektyvumas, Merkle medžio efektyvumas, validavimo klaidos, testų aprėptis.

| Pasiūlymas | Sprendimas | Commit |
|---|---|---|
| T1 bendras teisingumo rinkinys + `Bench` (veikia su v0.2 ir v0.3) | priimta | `7ab1a1c` |
| S1 `Hash.toHex` lentele vietoj `String.format` | priimta | `8514e78` |
| S2 midstate kasimo cikle (`PrefixHasher`) | priimta | `c09a524` |
| S3 Merkle vidiniai mazgai baitais | atmesta: ~6 % Merkle, ~1 % visos eigos | – |
| S4 netinkamo formato blokai / `null` → atmetimas, ne NPE | priimta | `d278416` |
| S5 `hasValidId` kešavimas | atmesta: −15 % laiko, bet 3 etapas nebeperskaičiuotų ID | – |
| S6 lygiagretus kasimas, S7 mažiau raundų / SHA-256 | atmesta (neįgyvendinta) | – |

### Palyginimas: v0.2 ir v0.3

Ta pati pradinė būsena (seed 20261029), tie patys duomenys ir `difficulty = 3`, `--fixed-time`; ta pati mašina
(OpenJDK 21, 4 branduoliai). Našumas – `test/bc/Bench.java` (naudoja tik v0.2 API, mediana iš 7).

| Matas | v0.2 (`0.2`) | v0.3 | Pokytis |
|---|---|---|---|
| savi testai | 40/40 | 53/53 | +13 testų |
| bendras teisingumo rinkinys (7) | 7/7 | 7/7 | tas pats |
| simuliacijos konsolės išvestis be laikų (1214 eil.), `report.txt`, `initial_state.csv` | – | – | **identiški** |
| paskutinio bloko hash | `0001bb1e…91b2a` | `0001bb1e…91b2a` | tas pats |
| 20 antraščių, difficulty 4: rasti nonce / bandymai | 1 276 057 | 1 276 057 | tie patys |
| kasimo greitis | 195 326 bandymai/s | 1 779 701 bandymai/s | **×9,1** |
| vienos 146 B antraštės maiša | 5 075 ns | 2 865 ns | −44 % |
| atmintis kasant | 6 441 B / bandymui | ~1 B / bandymui | **−99,98 %** |
| Merkle šaknis (100 tx) | 412 µs | 186 µs | −55 % (dėl S1) |
| visa simuliacija | 5 803 ms | 1 190 ms | **×4,9** |
| `prev_hash = null`, `addBlock(null)`, `submit(null)` | `NullPointerException` | `BAD_FORMAT` atmetimas | ištaisyta |

Paleidimas: `java -cp out/classes:out/test bc.CorrectnessSuite` ir `java -cp out/classes:out/test bc.Bench`
(Windows: `"out\classes;out\test"`). Matavimai priklauso nuo mašinos – svarbūs santykiai, ne absoliutūs skaičiai.

### Išvados

**Pagerėjo:** kasimas ~9 kartus greitesnis ir beveik be atminties išskyrimo, visa simuliacija ~5 kartus greitesnė,
Merkle – 2 kartus (šalutinis S1 efektas); netinkami blokai nebelaužia programos; atsirado bendras teisingumo rinkinys ir
lygiavertiškumo testai, kurie optimizuotą kodą lygina su v0.2 algoritmu.

**Nepasikeitė (sąmoningai):** visi rezultatai – maišos, nonce, bandymų skaičiai, blokai, balansai, ataskaitos –
sutampa su v0.2. Maišos algoritmas, serializacija, validavimo taisyklės ir laimėtojo taisyklė nekeisti. PoW
tikrinimas vis dar perskaičiuoja pilną maišą.

**Nepagerėjo / atmesta:** Merkle skaičiavimas baitais (S3) naudos beveik nedavė – laiką lemia pati maiša;
ID kešavimas (S5) būtų davęs 15 %, bet pažeistų etapų atskyrimą. Abu įrodymai – DI žurnale.

**Kas tapo sudėtingiau:** kasimo kelias (`PrefixHasher`) yra antras būdas skaičiuoti tą pačią maišą; jį saugo
lygiavertiškumo testai ir saugiklis, kuris kiekvieną sprendimą patikrina pilna maiša.

**DI pasiūlymas nėra teisingumo įrodymas:** pirmoje bendro rinkinio versijoje DI parašė klaidingą testo scenarijų
(krito abiejose versijose, pataisytas); kiekvienas priimtas pakeitimas tikrintas testais, mutacijomis ir v0.2
išvesties palyginimu, o ne remiantis DI teiginiu.

## Apribojimai

* Parašų nėra: `public_key` – tik imituotas identifikatorius, transakcijos nepasirašomos.
* `sequence` netikrinamas kaip griežtas siuntėjo skaitiklis (žr. „Validacija“).
* Konkurencija simuliuojama nuosekliai viename procese – tai ne P2P konsensusas (žr. aukščiau).
* Maišos funkcija lieka mokomoji; jos kriptografinis atsparumas netirtas (200 000 įvesčių be kolizijų nėra įrodymas).
* Didžiausią laiko dalį dabar sudaro pati maiša (`Hash.compress`); toliau greitinti galima tik keičiant algoritmą arba
  lygiagretinant, o tai v0.3 atmesta.
* Našumo matavimai atlikti vienoje mašinoje, be JMH; skaičiai orientaciniai.
