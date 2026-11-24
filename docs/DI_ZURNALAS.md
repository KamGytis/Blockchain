# DI naudojimo žurnalas

## Įrankis

| | |
|---|---|
| Įrankis | Claude Code (Anthropic), naršyklės sesija claude.ai/code |
| Modelis | `claude-opus-5-5` (nustatytas ir faktiškai naudotas modelis pagal sesijos duomenis) |
| Sesija | v0.3 darbas 2026-11-24 |
| Aplinka | Linux, OpenJDK 21.0.12.1, 4 branduoliai (matavimai atlikti joje) |


## DI pagalba ankstesniuose etapuose (atskleidimas)

v0.3 palyginimo atskaita – užfiksuota v0.2 (žyma `0.2`, commit `1d9999d`). **Ji nėra „be DI“.**

| Etapas | Autorius (rankomis) | DI (Claude Code) |
|---|---|---|
| v0.1 (2026-11-17) | maišos funkcija `Hash`/`HashUtil`, modelio klasės, `State`, `TxError`, `TxPool`, `TxRoot`, `Validation` pradinės versijos (commit'ai `2a5905a`–`9f4fed5`); vėliau „Fix: State“, „Fix: removed comments“ UTF-8 BOM pašalinimas, `Blockchain`, `Validator`, `Pow`, `Miner`, `sim.*`, `State` papildymas, `Math.cbrt` → `StrictMath.cbrt`, testai, skriptai,  Windows `.bat` ir `.gitattributes` (`e2d4f4c`) | README |
| v0.2 (2026-11-23) | komentarų formatavimas („Slight Fixes“, `1d9999d`), Merkel Medis | README |
| v0.3 (2026-11-24) | užduoties pateikimas, sprendimų priėmimas | peržiūra, matavimai, pasiūlymai, pakeitimai ir testai (žr. žemiau) |

## v0.3 sąveika

### Užklausa (2026-11-24)

DI buvo pateiktas visas 5 skyriaus („v0.3: patobulinimas su DI įrankiais“) tekstas su bendro teisingumo testų
rinkinio lentele, o kodas – visas projekto repozitorijas (DI jį skaito tiesiogiai). Esami reikalavimai – v0.1 ir v0.2
užduočių tekstai, pateikti ankstesnėse tos pačios sesijos užklausose.

Pasirinktos kryptys:
1. **kasimo ciklo efektyvumas** (S1, S2),
2. **Merkle medžio skaičiavimo efektyvumas** (S3),
3. **validavimo klaidos** (S4, S5),
4. **testų aprėptis** (bendras teisingumo rinkinys, lygiavertiškumo testai).

### 1 žingsnis – atskaitos matavimas ir profiliavimas

DI parašė `test/bc/Bench.java` (naudoja tik v0.2 API) ir paleido jį užfiksuotoje v0.2: kasimas 205 549 bandymai/s,
vienos 146 B antraštės maiša 4 594 ns, visa simuliacija 5 754 ms. Mikro-profilis vienam bandymui:

```
serialize()+getBytes (withNonce)        78 ns
Hash.hash(146 B) viso                 4837 ns
toHex (8x String.format)              1930 ns   <- ~40 % maišos laiko
pad(146 B)                              35 ns
hasLeadingZeros                          8 ns
```

DI išvada: (a) `String.format` hex konvertavimas sudaro ~40 % maišos laiko; (b) kasant kiekvieną kartą maišomi
visi 5 antraštės blokai, nors pirmieji 143 B (`prev_hash|timestamp|version|tx_root|`) paieškoje nekinta.

### Pasiūlymai ir sprendimai

| # | Kryptis | Pasiūlymas | Sprendimas | Commit |
|---|---|---|---|---|
| T1 | testai | bendras 5 skyriaus teisingumo rinkinys + našumo matavimas, veikiantys su v0.2 ir v0.3 | **priimta** | `7ab1a1c` |
| S1 | kasimas | `Hash.toHex`: lentelė vietoj `String.format("%08x")` | **priimta** | `8514e78` |
| S2 | kasimas | midstate: pastovaus antraštės prefikso būsena skaičiuojama kartą; nonce rašomas į baitus; nuliai skaičiuojami žodžiuose | **priimta** | `c09a524` |
| S3 | Merkle | vidiniai mazgai baitais, hex tik šaknyje | **atmesta** – naudos beveik nėra | – |
| S4 | validacija | netinkamo formato antraštė / `null` blokas / `null` tx atmetami su priežastimi, ne `NullPointerException` | **priimta** | `d278416` |
| S5 | validacija | `Transaction.hasValidId()` rezultato kešavimas | **atmesta** – prieštarauja reikalavimui | – |
| S6 | kasimas | kandidatų kasimas lygiagrečiai gijose | **atmesta** (neįgyvendinta) | – |
| S7 | kasimas | mažiau maišos raundų arba SHA-256 | **atmesta** (neįgyvendinta) | – |

### Priimti pakeitimai – kaip veikia ir kaip patikrinta

**T1 – bendras teisingumo rinkinys.** `CorrectnessSuite` – 7 testai pagal 5 skyriaus lentelę. Failas naudoja tik
v0.2 viešąją API, todėl jis be pakeitimų nukopijuotas į v0.2 (žyma `0.2`) ir ten paleistas: 7/7. DI pirmoje versijoje
padarė klaidą testo scenarijuje [6b]: tikėjosi, kad T1 (A→B 70) ir T2 (A→C 60) negali būti kartu, nors fonde buvo
ir B→A 30, kuri A balansą padidina iki 130. Testas krito **abiejose** versijose – tai buvo testo, ne kodo klaida;
scenarijus pataisytas (B→C vietoj B→A). Tai pavyzdys, kad DI sugeneruotas testas nėra teisingumo įrodymas.

**S1 – `toHex`.** Kiekvienas 32 bitų žodis verčiamas į 8 hex simbolius: imami 4 bitai (`v & 0xF`), pagal juos iš
lentelės `"0123456789abcdef"` paimamas simbolis, `v >>>= 4`; rašoma iš galo, todėl pradiniai nuliai išlieka.
*Patikra:* 1 000 000 atsitiktinių žodžių ir kraštiniai atvejai (`0`, `0xF`, `0x80000000`, `0xFFFFFFFF`…) lyginami
su senąja `String.format` realizacija; maišos testų vektoriai nepakito. Rezultatas: maiša 4 837 → 2 716 ns.

**S2 – midstate kasime** (išsamiai patikrintas pasiūlymas).
*Kaip veikia:* `Hash` apdoroja įvestį 32 B blokais, būseną `h` perduodama iš bloko į bloką. Jei įvesties pradžia
nekinta, pilnų jos blokų rezultatas visada tas pats. `PrefixHasher` jį apskaičiuoja vieną kartą, o kiekvienam
bandymui suspaudžia tik prefikso likutį + `nonce|difficulty` + užpildymą. Užpildyme įrašomas **visos** žinutės ilgis
(prefiksas + priesaga). Rastas sprendimas dar kartą patikrinamas įprasta pilna maiša (saugiklis).
*Ką ir kaip patikrinau:*
1. `PrefixHasher` = `Hash.hashBytes(prefix || suffix)` visiems prefiksų ilgiams 0..200 B (visos padėtys bloko ribos
   atžvilgiu) ir 40 atsitiktinių 0..80 B priesagų kiekvienam – 8 040 atvejų.
2. `Pow.search` palygintas su v0.2 algoritmu (etalonas teste): tie patys sprendimai, bandymų skaičiai ir `nextNonce`
   792 atvejams – skirtingi timestamp ilgiai, pradiniai nonce ties skaitmenų ribomis (9→10, 99 990,
   999 999 999 999 999 990), neigiami nonce, biudžetai 1..20 000.
3. **Mutacija:** užpildyme įrašius tik likučio ilgį (tipinė midstate klaida), krenta 25 testai, tarp jų tiesioginis
   lygiavertiškumo testas, o saugiklis `Pow.search` meta „midstate ir pilna maisa nesutampa“.
4. Ta pati pradinė būsena, duomenys ir sudėtingumas: `Bench` 20 antraščių difficulty 4 – tie patys nonce
   (`pow_nonces_hash` sutampa), tas pats bandymų skaičius 1 276 057; simuliacija `--fixed-time` – tas pats paskutinio
   bloko hash `0001bb1e…` kaip v0.2. Greitis: 205 549 → ~1,7 mln. bandymų/s.

**S4 – netinkamo formato blokai.** Peržiūrėdamas v0.2 validaciją DI pastebėjo, kad `checkHeader` kviečia
`h.prevHash().equals(...)` netikrinęs formato. Patvirtinta bandymu su žyma `0.2`:

```
antraste su prev_hash = null: NullPointerException
addBlock(null): NullPointerException
pool.submit(null) ir atmestuju ataskaita: NullPointerException (null atsidurdavo atmestųjų sąraše)
```

Po pataisymo visi trys – `InvalidBlockException: netinkamas bloko ar antrastes formatas …` arba `BAD_FORMAT`
be įrašo į sąrašą. Testai: 5 blogi `prev_hash` ir `tx_root` variantai (`null`, tuščias, per trumpas, ne hex,
per ilgas), `null` blokas ir antraštė; būsena ir aukštis nepakinta.

### Atmesti pasiūlymai – kodėl

**S3 – Merkle baitais.** Įgyvendinta, lygiavertiškumas patikrintas (0..300 lapų), išmatuota 3 kartus po 5 000
skaičiavimų (100 tx):

```
S2 (hex):    205.9 / 207.3 / 197.2 us (mediana)
S3 (baitai): 190.8 / 195.2 / 185.9 us
```

Pagerėjimas ~6 %, min/max intervalai persidengia. Visos simuliacijos profilyje Merkle užima ~18 %, taigi S3 sutaupytų
~1 %. Laiką lemia pati maiša (3 blokai kiekvienam mazgui), ne hex konvertavimas (jį jau pagreitino S1).
Antra Merkle realizacija šalia `levels()` padidintų nesutapimo riziką – **atmesta, kodas atšauktas**.

**S5 – `hasValidId` kešavimas.** Profilyje ID perskaičiavimas užėmė ~21 %. Įgyvendinta eksperimentiškai:
simuliacija 1 272 / 1 205 ms → 1 023 / 1 064 ms (~15 % greičiau), rezultatas tas pats. Vis dėlto užduotis reikalauja
pool etape tikrinti *perskaičiuotą* `transaction_id`, o prieš priimant bloką *iš naujo* patikrinti transakcijas.
Vieno proceso simuliacijoje tame pačiame objekte saugomas rezultatas reikštų, kad 3 etapas tik pakartotų 1 etapo
atsakymą. **Atmesta** – aiškus etapų atskyrimas svarbesnis už 15 %.

**S6 – lygiagretus kandidatų kasimas.** Užduotis nurodo, kad v0.2 pakanka nuoseklaus vykdymo, o laimėtojo taisyklė
(mažiausiai bandymų) yra deterministinė. Gijos pagreitintų kasimą, bet būtų sunkiau atkurti rezultatus ir paaiškinti
tvarką. **Atmesta, neįgyvendinta.**

**S7 – mažiau raundų / SHA-256.** Pakeistų maišos išvestį (sugriūtų testų vektoriai ir v0.2 palyginamumas), o SHA-256
pažeistų „savo maišos“ reikalavimą. **Atmesta, neįgyvendinta.**
