# Rynek Analiza - dodatek do BK-Rynek 3.0 (Minecraft 1.21.4, Fabric)

Osobny mod, który liczy ceny skupu i wpisuje je prosto w listę przedmiotów BK-Rynek.
Nie skompilowany na prawdziwym Minecraft/Fabric (tylko na atrapach API) i nie uruchomiony w grze.

## Jak zbudować
1. Repo z plikami budowania (`build.gradle`, `gradle.properties`, `settings.gradle`, `.github/workflows/build.yml`)
   i folderem `src/` z tej paczki. Build leci w zakładce Actions, jar jest w artefakcie "rynek-analiza".
2. Jar wrzuć do `mods/` razem z BK-Rynek.

## Panel (GUI)
- Klawisz **prawy Shift** (domyślnie) otwiera/zamyka panel. Klawisz **F8** (domyślnie) = start/stop analizy.
  Oba klawisze zmienisz w panelu, w zakładce **Ustawienia** (kliknij przycisk i naciśnij klawisz; Delete/Backspace = brak, Esc = anuluj),
  albo w Sterowaniu Minecrafta (kategoria "Rynek Analiza").
- Zakładka **Przedmioty**: lista przedmiotów z aktywnego profilu BK (wyszukiwarka, strony), dla każdego:
  aktualna cena skupu, pole **Marża ($)** (puste = marża domyślna), **Sugestia** z ostatniej analizy i przycisk **Użyj**
  (wpisuje sugestię do pola marży). Przyciski **Start analizy** (z wpisaną frazą wyszukiwania analizuje tylko pasujące) i **Stop**.
- Zakładka **Ustawienia**: klawisze, domyślna marża, podatek (0.97), mnożnik sugestii, pauza podświetlenia, podgląd na rynku, wznawianie BK.
- Podczas analizy panel nie otwiera się (komunikat w czacie) - zatrzymaj analizę klawiszem start/stop.
- Marże i sugestie są zapisywane w `config/rynek-analiza.json`.

## Wzory
- **Cena skupu** (wpisywana do BK jako `maxPrice`) = `floor( średnia 3 najtańszych ofert za sztukę * 0.97 - marża )`.
- **Sugerowana marża** (z 5 najtańszych ofert) = `(cena najdroższej z 5 - cena najtańszej) / 4` = typowy krok cenowy między
  kolejnymi ofertami, razy mnożnik z ustawień; zaokrąglenie do 10 (od 100 $ wzwyż) lub do pełnych $. Gdy ofert jest 3-4, rozstęp dzielony jest przez (liczba ofert - 1);
  poniżej 3 ofert brak sugestii.
  To moje założenie, nie wzór z BK - jeśli wolisz inny, wystarczy zmienić `computeSuggestedMargin` w `MarketAnalyzer.java`.

## Co dzieje się podczas analizy
BK-Rynek jest zatrzymywany (i wznawiany po analizie, jeśli działał). Dla każdego przedmiotu:
`/ah search <nazwa>` -> PPM w repeater dwa razy ("Najniższej ceny") -> skan -> cena za sztukę z każdej pasującej oferty ->
cena skupu i sugestia marży -> `maxPrice` wpisu w BK -> na końcu zapis configu BK i przeładowanie cen.
Mniej niż 3 oferty = cena skupu nie zostaje ustawiona.

Dopasowanie ofert jak w BK: najpierw zgodne id przedmiotu (BK wybiera wpisy po id, np. `enchanted_golden_apple`),
potem `AutoMarketScanner.matchesEntry` (nazwa, enchanty, lore, custom model data...) + zgodna liczba komponentów.
Zwykłe złote jabłko o nazwie "Zaklęte złote jabłko" nie przejdzie.

Zasięg skanu zależy od przedmiotu (na stałe, bez ustawienia): przedmioty niestackowalne (po 1 szt., np. hełmy) - skan kończy się,
gdy znaleziono 5 ofert (3 idą do ceny, 5 do sugestii), bo rynek jest posortowany od najniższej ceny; przedmioty stackowalne (np. złote jabłka) -
przeglądane są wszystkie strony, bo większy stack na dalszej stronie może mieć niższą cenę za sztukę.

## Podgląd na rynku
- Nad rynkiem pasek z ikoną i nazwą analizowanego przedmiotu, numerem w kolejce, stroną, liczbą ofert i trybem skanu.
- Oferty z 3 najtańszych (idą do ceny skupu) mają **żółtą** ramkę, 4. i 5. (tylko do sugestii) **pomarańczową**.
  Strona z podświetleniem jest chwilę trzymana (`highlightPauseTicks`, domyślnie 20 = 1 s).
- Po policzeniu w czacie: "Użyte oferty: str.1 r1 k1 = 950/szt (id "nazwa" xN); ..." oraz sugerowana marża. To ostateczny wybór;
  w trybie pełnego skanu podświetlenie na kolejnych stronach pokazuje stan chwilowy.
- Wyłączenie podglądu: zakładka Ustawienia albo `"showOverlay": false`.

## Komendy (opcjonalnie)
`/analiza start [fragment]`, `/analiza stop`, `/analiza lista`, `/analiza marza <kwota> <fragment>`, `/analiza marza_domyslna <kwota>`.

## Do sprawdzenia przy pierwszym teście
1. Prawy Shift otwiera panel i widać w nim przedmioty z BK (jeśli nie - komunikat błędu w panelu).
2. Wpisanie marży w polu zapisuje się po zamknięciu panelu; po analizie w kolumnie "Sugestia" pojawia się wartość.
3. Fraza po `/ah search` to domyślnie nazwa przedmiotu bez kolorów. Jeśli serwer szuka inaczej, wpisz własną w `search` w configu.
4. Cena z lore: używany jest `lore_regex` z profilu BK, a gdy go brak - pierwsza liczba po słowie "Koszt".
5. BK porównuje cenę za sztukę tylko przy włączonym trybie "Unit" w profilu.
