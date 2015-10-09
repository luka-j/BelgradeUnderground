package rs.luka.belgradeunderground;

import rs.luka.belgradeunderground.data.*;
import rs.luka.belgradeunderground.io.UserIO;
import rs.luka.belgradeunderground.model.Station;

import java.io.IOException;

/*
 * a) struktura podataka: videti package model. Nije 'pogodna' u smislu nije graf (iako je blisko), s kojim bi bilo
 * lakse raditi u smislu koristiti poznate algoritme
 * b) postaviti Config#WALKING_ENABLED na false (nije testirano ovako, mozda se provukla neka greska)
 * c) (i) postaviti Config#WALKING_ENABLED na true
 *    (ii) videti ispod
 */

/*
 * Algoritam:
 * Ovo je verovatno prvi program koji sam pisao od osnove, tj. modela i nisam pokretao skoro dva dana dok sve nije bilo
 * gotovo. Izmedju ostalog, zato je ispalo nesto sto nije graf out-of-the-box (Station funkcionise samo preko
 * getNextBestGuess(es)). Moglo je sve to da se promeni tako da kod s Rozete moze doslovno da se kopira.
 * Optimalno resenje bi verovatno bio neki geneticki algoritam, ali s obzirom da o njima ne znam nista sem najosnovnijih
 * karakteristika s Vikipedije, gotovo sigurno ne bih nista postigao.
 * A* sam nameravao da napisem, cim podatke organizujem kako treba. Dijkstra is too mainstream.
 * U nekom trenutku mi je sinula divna ideja da mozda moze i bolje, s obzirom da radim sa linijama i stanicama o kojima
 * znam vise nego jednostavno 'to su cvorovi i ivice koji imaju tu i tu tezinu'. Posto ovo ne smatram 'ozbiljnim'
 * programom u smislu da ce imati siroku upotrebu van ove prijave (makar za sad), sto ne bih probao nesto drugacije.
 * Poznati algoritmi nisu ni izbliza toliko zanimljivi, a svaki koji garantuje optimalan rezultat je uz to verovatno i
 * prespor.
 * Ni u jednom trenutku nisam bio siguran sta tacno pisem, pa je maltene sve izdvojeno u konstantu (ili u slucaju
 * Paths-a, ceo enum sa korisnim parametrima), tako da se moze lako promeniti.
 * Do resenja se moze doci na dva razlicta nacina (pritom ne racunam PathTree): Paths i PathQueue. Smatram da je prvi
 * 'savrseniji', valjda zato sto sam mnogo vise vremena potrosio na njega. Na kraju nisam stigao da ga zavrsim kako
 * treba. PathQueue sam ispisao za manje od pola sata i logicno, i dalje nisam ubedjen da to zapravo funkcionise.
 * Probavao sam, i probavao, i probavao, i izgleda da radi. Ima par slucajeva gde ne pronalazi optimalne putanje, ali
 * je premasio moja ocekivanja, i sto se puta i sto se brzine tice. Ostavljam oba, i mogucnost da se lako promeni u
 * Configu.
 * Sto se kompleksnosti oba algoritma tice, brzi su. Zaista, retko prelaze 0.3s @ 3.2GHz, a za krace putanje
 * vreme im je ispod 0.1s. Ne, nemam pojma kolika je zapravo big-O slozenost niti sam sasvim siguran kako bi se ponasali
 * za veci dataset, ali je moja pretpostavka da bi se dobro snasli.
 */

/*
 * Korisne konstante i promenljive:
 * Paths#minEfficiency, Paths#MAX_EFFICIENCY_FAILS. Paths#REDUCE_EFFICIENCY_FACTOR - tri vrednosti koje najvise uticu
 * na rezultat ako se koristi Paths, na koje se odnosi enum PathConfigs. I dalje nisam nasao optimalne vrednosti.
 * Line#COST_TROLLEY, Line#COST_TRAM, Line#COST_BUS, Line#COST_WALK - koeficijenti koji se odnose na tip prevoza
 * Line#DIRECTIONS_LOOKAHEAD - koliko stanica unapred se gleda kada se racuna smer linije
 * Station#MAX_WALKING_DISTANCE - u metrima, po uslovima zadatka 200, iako smatram da treba biti makar duplo veca
 * Station#DELTA_WEIGHT - kolika je najveca razlika u tezinama za koju se moze smatrati da obe putanje mogu biti optimalne
 * Station#BEARING_LEVELS, Station#BEARING_WEIGHTS - tezina koju nosi (odstupanje od) smer(a)
 * Station.Link#USE_COST_FOR_WEIGHT - definise u kom odnosu su trosak (cost) i tezina (weight)
 * Station.Link#SWITCH_COST, Station.Link#STOP_COST - koliki trosak predstavlja jedna stanica u zavisnosti da li se preseda
 * Config#*
 */

/*
 * Pokretanje: 1) sa argumentima, prvi predstavlja direktorijum u kojem se nalaze json fajlovi (opciono)
 *                                drugi i treci su id-ovi pocetne i krajnje stanice
 *             2) graficki, ako se ne prosledi nikakav argument, defaultuje na dijaloge
 * U zavisnosti od nacina unosa ispisuje podatke: ako su uneseni preko argumenata stampa na terminal, ako su uneseni
 * graficki, ispisuje ih koristeci dijalog.
 */

/*
 * Struktura programa:
 * data.Base - svi podaci, pocetna i krajnja stanica
 *      Finder - legacy, metode koje vrte i opciono rollback-uju PathTree
 *      PathQueue - prosiruje PriorityQueue, sortira po efikasnosti
 *      Paths - unapredjena verzija PathTree, nedovrsen jer koristi PriorityQueue za efikasnost umesto referenci/cuvanja
 *              optimalnog pretka kao dela objekta
 *      PathTree - legacy, drvo koje se sastoji od mogucih putanja
 * io.BaseIO - ucitavanje i cuvanje baze iz/u cache/-a
 *    JsonLoader - ucitavanje baze iz json fajlova
 *    LogUtils - postavljanje fajla i handlera za logger kada debugger nije dovoljan (ili praktican)
 *    UserIO - citanje ulaza iz argumenata ili graficki
 * model.LatLng - geografski podaci, predstavlja lokaciju i sve metode vezane za njih, 'skupe' tacke
 *       Line - predstavlja liniju, id, stajalista koja posecuje, tip vozila, smer, specijalne slucajeve (initial i walking)
 *       Station - predstavlja stanicu i veze do drugih stanica, odredjuje njihovu tezinu i vraca onog s najmanjom
 * Config - cuva konstante bitne za rad programa
 * Main - pokrece odgovarajucu metodu pretrage
 */

/*
 * Ostali komentari:
 * Verovatno preterana upotreba ?: Do sad sam ga retko koristio, ali se pokazao prilicno kompaktan u dosta slucajeva
 * Postoje slucajevi gde je dokumentacija pogodnija za komentar
 * Todo-ovi su negde pisani kod stvari koje nisu zapravo todo zbog boje teksta koja je zuta (za razliku od bledosive)
 * Margina je 120 karaktera. 80 mi cesto bude preusko i prevelika belina (ili 'crnina') mi ostaje desno
 * Zahvaljujem se Davidu sto mi je pomogao da ne napravim neke greske (https://github.com/geomaster/beoprevoz)
 * Izvinjavam se zbog nedostatka č, ć, ž, đ, š. Uglavnom me ne mrzi da koristim compose key, ali ovo sam uglavnom radio
 * uvece ili nocu, pa nisam imao snage da nabadam alt svaki put.
 */

/**
 * Glavna klasa, kao što joj samo ime kaže
 * Created by luka on 14.9.15.
 */
public class Main {

    /**
     * Poziva metodu za ucitavanje, i onda u zavisnosti od Configa odgovarajuću metodu za pronalaženje puta
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        long start = System.currentTimeMillis();
        UserIO.read(args);
        //locationTest();
        //cvijicevaTest();

        long algoStart = System.currentTimeMillis();
        switch (Config.FINDING_METHOD) {
            case Config.PATHTREE_SIMPLEFIND:
                simplefind();
                break;
            case Config.PATHTREE_QUICKFIND:
                quickfind();
                break;
            case Config.PATHS:
                paths(PathConfigs.STRICT);
                break;
            case Config.PATHQUEUE:
                pathqueue();
        }

        long algoEnd = System.currentTimeMillis();
        if(Config.DEBUG) System.out.println("Time spent in algorithm " + (algoEnd-algoStart)/1000.0);

        long end = System.currentTimeMillis();
        System.out.println("Total time: " + (end - start) / 1000.0);
    }

    //slede metode sa nedeskriptivnim imenima, u principu pokrecu odredjeni algoritam i ispisuju rezultat

    private static void simplefind() {
        if(Config.DEBUG) System.out.println("Using simplefind");
        Finder finderSimple = new Finder(Base.getInstance().getStart());
        PathTree.Result resSimple;
        resSimple = finderSimple.dumbFind();
        resSimple.print();
    }

    private static void quickfind() {
        if(Config.DEBUG) System.out.println("Using quickfind");
        Finder finder = new Finder(Base.getInstance().getStart());
        PathTree.Result res;
        res = finder.quickFind();
        res.print();
    }

    private static void paths(PathConfigs params) {
        if(Config.DEBUG) System.out.println("Using paths");
        Paths paths = params.initPath(Base.getInstance().getStart());
        while(!paths.isComplete())
            paths = paths.getNext();
        paths.getPath().print();
    }

    private static void pathqueue() { //najslicnije NO_REDUCTIONS
        new PathQueue(Base.getInstance().getStart()).solve().reconstruct().print();
    }

    /**
     * Predstavlja parametre na osnovu kojih se odredjuje da li je efikasnost premala, kako je treba menjati i pocetnu
     * minimalnu vrednosti i time najvise utice na rezultat.
     * Potrebno testiranje
     */
    private enum PathConfigs {
        NO_REDUCTIONS(0.5, 2, 1),
        NORMAL(0.5, 2, 1.2),
        STRICT(0.8, 2, 1.1),
        LENIENT(0.35, 2, 1.2),
        TOLERATE_FAILS(0.6, 4, 1.1),
        MAX_START_EFFICIENCY(1, 3, 1.2),
        ALWAYS_TEST_EFFICIENCY(0.5, 1, 1.1);

        final double minEfficiency, reduceFactor;
        final int maxFails;

        PathConfigs(double minEfficiency, int maxFails, double reduceFactor) {
            this.minEfficiency = minEfficiency;
            this.maxFails = maxFails;
            this.reduceFactor = reduceFactor;
        }

        public Paths initPath(Station start) {
            return Paths.init(start, minEfficiency, maxFails, reduceFactor);
        }
    }
}
