# Blockchain v0.1

Mokomoji vieno proceso blokų grandinė Java kalba: savo maišos funkcija, transakcijų fondas, blokai su
`tx_root`, darbo įrodymas (PoW) ir konsolės suvestinė. Išorinių bibliotekų nėra.

## Paleidimas

Reikia JDK 17 ar naujesnio (tikrinta su OpenJDK 21).

```bash
./run.sh
```

```bash
./test.sh
```

Be bash (pvz., Windows PowerShell / cmd):

```bash
javac -encoding UTF-8 -d out/classes src/bc/*.java src/bc/core/*.java src/bc/hash/*.java src/bc/model/*.java src/bc/sim/*.java
```

```bash
java -cp out/classes bc.App
```

Parinktys (`java -cp out/classes bc.App --help`):

| Parinktis | Numatyta | Reikšmė |
|---|---|---|
| `--users N` | 1000 | vartotojų skaičius |
| `--txs N` | 10000 | sugeneruojamų transakcijų skaičius |
| `--block-size N` | 100 | daugiausia transakcijų bloke |
| `--difficulty N` | 3 | kiek pradinių `0` turi turėti bloko hash (hex) |
| `--seed N` | 20261029 | generavimo seed |
| `--max-amount N` | 50000 | didžiausia generuojama mokėjimo suma |
| `--invalid-rate X` | 0.01 | tyčia sugadintų transakcijų dalis (atmetimo logikai parodyti) |
| `--out DIR` | `out` | kur rašyti `initial_state.csv` ir `report.txt` |
| `--fixed-time` | išjungta | `timestamp` = bloko aukštis, todėl visi bloko hash atkuriami |

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
3. Kiekviena transakcija pateikiama į fondą. Priėmimo metu tikrinama struktūra (hex laukai, `amount > 0`,
   `sequence ≥ 0`, žinomi vartotojai, siuntėjas ≠ gavėjas, ID = maiša(turinio)), ar ID nėra fonde ir ar nėra
   jau patvirtintas. Netinkamos atmetamos su priežastimi (`TxError`).
4. Kol fonde yra įvykdomų transakcijų:
   * fondas sumaišomas tuo pačiu `seed` atsitiktinumu ir iš jo renkama iki `block-size` transakcijų. Kiekviena
     kandidatė tikrinama su **darbine būsenos kopija, į kurią jau įskaityti anksčiau į tą patį bloką parinkti
     mokėjimai** – todėl keli to paties vartotojo mokėjimai bloke negali viršyti jo balanso;
   * nepakankamo balanso transakcija lieka fonde (laikina klaida – vėliau siuntėjas gali gauti lėšų);
   * apskaičiuojamas `tx_root`, nuosekliai didinant `nonce` nuo 0 kasamas blokas;
   * `Blockchain.addBlock` bloką dar kartą **pilnai patikrina** (versija, `prev_hash`, `difficulty`, ne tuščias,
     `tx_root`, perskaičiuotas PoW, kiekvienos tx struktūra, dublikatai bloke ir grandinėje, balansai, bendra
     suma, neneigiami balansai) ir tik tada atomiškai pakeičia būseną; įtrauktos transakcijos išimamos iš fondo.
5. Kai nebelieka nė vienos įvykdomos transakcijos (paskutinis blokas gali būti nepilnas), išspausdinama
   suvestinė. Visos fonde likusios transakcijos yra neįvykdomos galutinėje būsenoje – `report.txt` kiekvienai
   nurodo, kiek siuntėjas turi ir kiek reikia.

## Modelis

* **Account modelis**: `State` = `public_key → balansas` (`long`). Transakcija tik perkelia `amount` nuo
  siuntėjo gavėjui, todėl bendra suma nekinta. Invariantai v0.1: balansai visada `≥ 0`, bendra lėšų suma
  lygi pradinei, kiekviena sugeneruota transakcija yra arba įtraukta, arba atmesta, arba likusi fonde.
  Jie tikrinami kiekviename `addBlock` ir simuliacijos pabaigoje (pažeidus – programa baigiasi klaida).
* **Transakcija**: `sender`, `receiver` (64 hex), `amount`, `sequence`, `id`.
* **Bloko antraštė** (6 laukai): `prev_hash`, `timestamp` (Unix sekundės), `version` (=1), `tx_root`,
  `nonce`, `difficulty`. Pirmojo bloko (aukštis 0) `prev_hash` = 64 nuliai.
* **Blokas** = antraštė + transakcijų sąrašas. Bloko hash nesaugomas – jis visada skaičiuojamas iš antraštės.

| Klasė | Paskirtis |
|---|---|
| `bc.hash.Hash`, `HashUtil` | savo maišos funkcija, UTF-8 maiša, hex pagalbinės |
| `bc.model.*` | `User`, `Transaction`, `BlockHeader`, `Block` |
| `bc.core.State` | balansai, `check`/`apply`, suma |
| `bc.core.Validation`, `Validator` | transakcijos struktūros ir priėmimo į fondą taisyklės |
| `bc.core.TxPool` | laukiančios ir atmestos transakcijos |
| `bc.core.TxRoot` | `tx_root` |
| `bc.core.Pow` | kasimas ir PoW patikra |
| `bc.core.Miner` | transakcijų atranka į bloką ir bloko kasimas |
| `bc.core.Blockchain` | grandinė ir pilna bloko patikra |
| `bc.sim.*` | parametrai, deterministinis generatorius, simuliacija ir suvestinė |

## Kodavimas prieš maišą

Kūrimui ir patikrai naudojamos tos pačios funkcijos (`Transaction.serialize`, `BlockHeader.serialize`).

* **Transakcija**: `sender|receiver|amount|sequence`, koduojama UTF-8, be eilutės pabaigos.
  `transaction_id = H(UTF-8(serializacija))`; pats ID į įvestį neįtraukiamas.
  Pvz. `a12f|b34e|100|5` → `7437bf5156dfb2e4b65440ad4c2f6b19e6fdd1670e14adfbe70883337ef5d363`
  (`a12f`, `b34e` – sutrumpinti mokomieji identifikatoriai; programoje jie 64 hex).
* **Bloko antraštė**: `prev_hash|timestamp|version|tx_root|nonce|difficulty`, UTF-8, be eilutės pabaigos.
  Bloko hash = `H(UTF-8(serializacija))`, o ne objekto adreso ar `toString()` maiša.
* Skirtukas `|` laukų reikšmėse negali pasitaikyti: hash laukai turi tik `0-9a-f` (tikrinama `HashUtil.isHash`),
  skaičiai rašomi `Long.toString` – dešimtainiai, be tarpų, pliuso ir nereikalingų pradinių nulių. Todėl
  `1|23` ir `12|3` duoda skirtingus baitus (žr. testą). Laisvo teksto laukų v0.1 nėra.
* **`tx_root`** = `H(id_1 id_2 … id_n)` – bloko transakcijų ID sujungti eilės tvarka. Kiekvienas ID lygiai 64
  simbolių, todėl sujungimas vienareikšmis. `tx_root` yra antraštėje, tad pakeitus bet kurią transakciją ar jų
  tvarką keičiasi ir bloko hash.

## Maišos funkcija

Versija: **KGHash v2 (8 raundai, message schedule, K konstantos, StrictMath)** (`Hash.VERSION`).

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

Testų vektoriai (užfiksuoti `test/bc/Tests.java`):

| Įvestis (UTF-8) | Maiša |
|---|---|
| `""` | `e209e47debe9cdafc922ad38f2bdb0105fa7a00a7130f088c4b797095a2116f2` |
| `abc` | `699b56920847030b53e5f67a423b8add976670faef02625dd72a9f9e8785c111` |
| `a12f\|b34e\|100\|5` | `7437bf5156dfb2e4b65440ad4c2f6b19e6fdd1670e14adfbe70883337ef5d363` |
| antraštė `0…0\|1700000000\|1\|<H("")>\|42\|3` | `2da8fbc2b786bc949f3ab6f00ea6367dd0a4dfb9d671d2bc07dc40f0703f18c9` |

## Darbo įrodymas

* Sąlyga: bloko hash hex užrašas prasideda bent `difficulty` nulių (pagrindinis bandymas `difficulty = 3`,
  t. y. `000…`, vidutiniškai ~4096 bandymai).
* `nonce` didinamas nuosekliai nuo 0, kiti antraštės laukai (ir `timestamp`) vieno bloko paieškoje fiksuoti.
* Gauta maiša niekaip nekeičiama. `Pow.isValid` ir `Blockchain.addBlock` maišą visada **perskaičiuoja** iš
  antraštės laukų; išsaugotu hash nepasitikima (jis net nesaugomas).
* Tai mokomasis sudėtingumo apibrėžimas; Bitcoin `target` ir sudėtingumo perskaičiavimas nerealizuoti.

## Generavimo seed ir atkuriamumas

Numatytas seed: **20261029**. Naudojamas `java.util.Random`, kurio algoritmas apibrėžtas Java specifikacijoje,
todėl tas pats seed visur duoda tuos pačius vartotojus, transakcijas, jų ID ir transakcijų atranką į blokus.
Konsolėje rodomas `tx_set_fp` – visų sugeneruotų transakcijų ID maiša; su numatytais parametrais ji visada
`9b19c7458f52b60f2676146510e28536a2c7489c229ec4a6d02cab72ea2a6ca9`.

Bloko hash priklauso ir nuo `timestamp`, todėl tarp paleidimų skiriasi (`tx_root` – ne). Su `--fixed-time`
(`timestamp` = aukštis) atkuriama visa grandinė: su numatytais parametrais paskutinio bloko hash visada
`000e250d87888e0574de905fde4d7b16a3bff82fa94e90a8fc01f847c015155b`.

## Išvesties pavyzdys (`./run.sh`, numatyti parametrai)

```
=== Blockchain v0.1 ===
hash:        KGHash v2 (8 raundai, message schedule, K konstantos, StrictMath)
seed:        20261029
parametrai:  users=1000 txs=10000 block_size=100 difficulty=3 max_amount=50000 invalid_rate=0.01
tx_set_fp:   9b19c7458f52b60f2676146510e28536a2c7489c229ec4a6d02cab72ea2a6ca9  (visu sugeneruotu tx ID maisa)
fondas:      priimta 9900, atmesta priimant 100

Blokas #0 | tx=100
  prev_hash:  0000000000000000000000000000000000000000000000000000000000000000
  tx_root:    7397dfd167788f658b2e00ff8ea1df1ed1f5eb89a111ca61d3dcf9ce1cf40988
  hash:       000e5039a97e8109b018c0e4e233e60dcb689b23eb35b08f50b8b209d84e395a
  difficulty=3 nonce=10042 bandymai=10043 laikas=101.6 ms timestamp=1794918067

...

Blokas #97 | tx=25
  prev_hash:  ...
  tx_root:    a1c9e63c7c519f36119c1b23e8b692caa311ab7cda2905d686cab2e192dd7bcb
  hash:       00008a1100095d850c70f8614be68724a077eb041dcc722d5dc7b46030b885e6
  difficulty=3 nonce=727 bandymai=728 laikas=3.8 ms timestamp=1794918070

=== Suvestine ===
vartotojai:            1000
transakcijos (sugen.): 10000
priimti blokai:        98
itrauktos tx:          9725
atmestos tx:           100
    UNKNOWN_RECEIVER       20 (nezinomas gavejas)
    SELF_TRANSFER          19 (siuntejas sutampa su gaveju)
    NON_POSITIVE_AMOUNT    25 (suma ne teigiama)
    BAD_ID                 22 (transaction_id neatitinka turinio)
    DUPLICATE_IN_POOL      14 (ID jau yra fonde)
likusios fonde tx:     175
    INSUFFICIENT_BALANCE   175 (nepakankamas balansas, galutineje busenoje)
pradine lesu suma:     481767596
galutine lesu suma:    481767596  (sutampa)
maziausias balansas:   225
paskutinio bloko hash: 00008a1100095d850c70f8614be68724a077eb041dcc722d5dc7b46030b885e6
kasimo laikas:         2.033 s
bendras laikas:        2.981 s
patikra: itrauktos+atmestos+likusios = 10000 = sugeneruota 10000 (OK)
pilnas atmestu/likusiu tx paaiskinimas: out/report.txt
pradine busena: out/initial_state.csv
```

Paskutinis blokas (#97) nepilnas – 25 transakcijos. 175 likusios transakcijos yra neįvykdomos galutinėje
būsenoje, pvz. iš `out/report.txt`: `... ,7199,0,INSUFFICIENT_BALANCE (turi 4845, reikia 7199)`.

## Testai (`./test.sh`)

```
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

Rezultatas: 11 praejo, 0 nepavyko
```

Rankomis patikrinamas atvejis (bloko dydis 2, `difficulty = 2`, fondo tvarka):

| | A | B | C | Pastaba |
|---|---|---|---|---|
| Pradžia | 100 | 50 | 0 | suma 150 |
| Blokas 0: A→B 30, A→C 50 | 20 | 80 | 50 | A→B 60 nebetelpa (blokas pilnas) |
| Blokas 1: B→C 80, C→A 10 | 30 | 0 | 120 | A→B 60 praleista: 20 < 60 |
| Blokas 2: C→A 15 (nepilnas) | 45 | 0 | 105 | suma 150 |
| Lieka fonde: A→B 60 | | | | 45 < 60 → `INSUFFICIENT_BALANCE` |

## Apribojimai ir v0.2 planas

* Parašų nėra: `public_key` yra tik imituotas identifikatorius, transakcijos nepasirašomos.
* `sequence` užtikrina ID unikalumą, bet v0.1 netikrina jo eiliškumo (replay apsauga – per ID dublikatų patikrą).
* v0.2: validavimo taisyklės bus išskirtos į atskiras procedūras, pridėti neigiami testai kiekvienai taisyklei.
