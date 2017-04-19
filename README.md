# BelgradeUnderground
### Nedelja Informatike u MG v2.0 - rešenje zadatka

## Problem
Dat je json fajl sa podacima o linijama u gradskom prevozu (tj. bio je dat negde, ali ga u trenutku pisanja ovog readme-a ne mogu naći). Treba pronaći najbrži put između dve stanice koristeći linije gradskog prevoza.

## Princip
Osnovni pojmovi: 
Line - predstavlja liniju prevoza i čuva stajališta na kojima ona staje
Station - predstavlja jedno stajalište i čuva linkove do sledećih
Station.Link - predstavlja link između dva stajališta, koristeći neku od linija.

Pretvaram JSON u improvizovani text-based format u kom čuvam više podataka, poput osnovne težine svakog linka pri prvom pokretanju (može da potraje). Pri svakom sledećem, učitavam podatke iz generisanog fajla i gradim model.

Osnovna težina linka najviše zavisi od udaljenosti, ali se i tip prevoza uzima u obzir (mislim da negde postoji flag kojim se to može isključiti, jer udaljenost ne mora nužno da bude relevantna). Kada se traži put, u obzir se uzima ugao između trenutne pozicije i cilja i trenutne pozicije i linka kao najveći indikator valjanosti puta. Heuristika nije optimalna, ali je uglavnom zadovoljavajuća.

Postoje dva metoda za pronalaženje puta, data.Paths i data.PathQueue, u kojima je objašnjeno kako funkcionišu. Kako nisam stigao da dovršim i počistim kod do roka, malo je haotično. Ukratko, Paths bira onaj link sa najmanjom cenom i pri svakom koraku računa efikasnost kao ukupna_cena/pravolinijsko_rastojanje, sve dok efikasnost ne ode ispod određenog praga, kada radi rollback na prethodnu najefikasniju putanju. Cena korišenja linka je određena udaljenošću i tipom prevoza i značajno se povećava ako je u pitanju presedanje. PathQueue je PriorityQueue na čijem je vrhu putanja sa najvećom efikasnošću, koja se pri svakoj iteraciji nastavlja linkom sa najmanjom cenom, i ovo se vrti sve dok se ne dođe do cilja.


## Struktura
io.\* - učitavanje, ispis fajlova

data.\* - podaci i algoritmi

model.\* - model podataka
