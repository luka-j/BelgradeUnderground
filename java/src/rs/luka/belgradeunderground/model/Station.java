package rs.luka.belgradeunderground.model;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.data.Base;
import rs.luka.belgradeunderground.io.BaseIO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Predstavlja jednu stanicu i metode za odredjivanje najbolje sledece. Zajedno sa Paths/PathQueue odredjuje najbolju
 * rutu. Sprecava ponavljanje istog puta (tj. ne dozvoljava da se ide od ove do druge stanice koristeci istu liniju vise
 * puta) koriscenjem lista i iteratora za svaku liniju.
 * Videti Link za terminologiju.
 * Created by luka on 14.9.15.
 * @see rs.luka.belgradeunderground.model.Station.Link
 */
public class Station {

    public static final int MAX_WALKING_DISTANCE = 200; //200m je cesto premalo, makar za trase za koje sam ja probavao
    //posto zadatak kaze tako, ostavljam na 200, ali bolje radi za vece udaljenosti (400-500m), jer se setanje vec
    //dovoljno penalizuje i uglavnom ostaje kao poslednja moguca opcija
    /**
     * @see #getNextBestGuesses(Line)
     */
    public static final double DELTA_WEIGHT = Link.USE_COST_FOR_WEIGHT ? 300 : 800;
    //Ne svidja mi se ideja da trosak za bearing racunam linearno. Treba zavrsiti ovo pre vikenda, pa cu se drzati
    //intuicije umesto matematickih dokaza (sorry)
    public static final int[] BEARING_LEVELS = new int[] {10, 20,   30,   45,   90};
    public static final int[] BEARING_WEIGHTS = new int[] {0,  1500, 4000, 8000, 20000, 60000}; //keep things aligned please
    //P. S. nisam zavrsio pre vikenda
    //todo naci funkciju koja ce ublaziti prelaz izmedju 9 i 11, 19 i 21 i sl. i pritom biti dovoljno slicna trenutnoj
    //todo metodi racunanja tezine

    private final int id;
    private final String name;
    private LatLng location;
    private final List<Link> links; //lista linkova in no particular order
    /**@deprecated */private boolean areLinksSorted = false;
    private boolean bearingsSet = false;

    /**
     * Cuva linkove sortirane po tezini za svaku liniju
     */
    private final Map<Line, List<Link>> sortedLinks = new HashMap<>(); //nisam ovo bas precizno osmislio na pocetku
    /**
     * Cuva iteratore (indekse) za svaku liniju koji oznacavaju vec isprobane puteve
     */
    private final Map<Line, Integer> iterators = new HashMap<>(); //oh well, trebalo bi da funkcionise i ovako
    //looking back, i nije tako lose

    /**
     * Linkove i lokaciju je potrebno naknadno podesiti (nakon ucitavanja linija)
     * @param id identifikacioni broj stanice
     * @param name user-friendly ime stanice
     */
    public Station(int id, String name) {
        this.id = id;
        this.name = name;
        links = new ArrayList<>();
    }

    public Station(String machineString) {
        String[] fields = machineString.split(BaseIO.FIELD_SEPARATOR);
        id = Integer.parseInt(fields[0]);
        name = fields[1];
        location = new LatLng(fields[2]);
        if(fields.length > 3) { //jer postoje cetiri kruzne bez smera B: 57, 405L, 522, 700
            String[] linkStrs = fields[3].split(BaseIO.ARRAY_SEPARATOR);
            links = new ArrayList<>(linkStrs.length);
            for (String linkStr : linkStrs) links.add(new Link(linkStr));
        } else {
            links = new ArrayList<>(0);
        }
    }

    /**
     * Koristi se pri deserijalizaciji
     */
    public void loadLinks() {
        links.forEach(Station.Link::loadDataFromString);
    }

    /**
     * Dodaje liniju stanici i pravi linkove
     * @param l linija
     */
    public void addLine(Line l) {
        List<Station> nextStations = l.getStationAfter(this);
        links.addAll(nextStations.stream().map(station -> new Link(station, l)).collect(Collectors.toList()));
    }
    public void setLocation(double lat, double lng) {
        location = new LatLng(lat, lng);
    }

    /**
     * Pravi linkove za setnju izmedju stanica, ako su na odgovarajucoj udaljenosti i setanje je ukljuceno u Configu
     * @param stationSet kolekcija stanica
     */
    public void registerStations(Collection<Station> stationSet) { //valjda je originalno bio Set, pa je ostalo ime, nebitno
        stationSet.stream().filter(s -> Config.WALKING_ENABLED && !this.equals(s) && location.distanceBetween(s.getLocation()) < MAX_WALKING_DISTANCE)
                .forEach(s -> links.add(new Link(s, Line.getWalking())));
    }

    /**
     * @deprecated izuzetno sporo
     * @param goal
     */
    public void sortLinksAccordingTo(final LatLng goal) {
        final Station This = this;
        links.sort((link1, link2) -> {
            double direction1, direction2, diff1, diff2;
            if (link1.usingLine == null)
                direction1 = This.getLocation().bearingTo(link1.toStation.getLocation());
            else
                direction1 = link1.usingLine.getDirectionAt(This);
            if (link2.usingLine == null)
                direction2 = This.getLocation().bearingTo(link2.toStation.getLocation());
            else
                direction2 = link2.usingLine.getDirectionAt(This);
            diff1 = Math.abs(direction1 - This.location.bearingTo(goal));
            diff2 = Math.abs(direction2 - This.location.bearingTo(goal));
            if (diff1 < diff2)
                return -1;
            if (diff1 > diff2)
                return 1;
            return 0;
        });
        areLinksSorted = true;
    }

    /**
     * Postavlja razliku smera linkova i cilja i odredjuje im tezinu (BEARING_WEIGHT)
     */
    private void setBearingAccordingToGoal() {
        for(Link l : links) {
            double direction;
            if(l.usingLine == null || l.usingLine.isInitial() || l.usingLine.isWalking())
                direction = getLocation().bearingTo(l.toStation.getLocation());
            else
                direction = l.usingLine.getDirectionAt(this);
            l.setBearingDiff(direction - location.bearingTo(Base.getInstance().getGoal().getLocation()));
        }
        bearingsSet = true;
    }

    /**
     * Sortira linkove (mapa sortedLinks) u odnosu na tezinu koristeci datu liniju
     * @param line data linija
     */
    private void setEstimates(Line line) {
        if(!bearingsSet) //smer se ne menja u zavisnosti od linije
            setBearingAccordingToGoal();
        final Line nonNullLine = line == null ? Line.getInitial() : line; //pisano pre nego što sam se setio da uvedem initial i walking kao specijalne slučajeve
                                                                          // nisam siguran gde sam sve koristio ovo, a nemam vremena za refactoring

        List<Link> links = new LinkedList<>(this.links);
        links.sort((o1, o2) -> {
            if(o1.calculateWeightFor(nonNullLine) < o2.calculateWeightFor(nonNullLine))
                return -1;
            if(o1.calculateWeightFor(nonNullLine) > o2.calculateWeightFor(nonNullLine))
                return 1;
            return 0;
        });

        //ako postoje dva puta do jedne stanice, moze da se desi da walking bude pre nekog prevoza zbog nacina racunanja smera
        //nisam siguran da li je ovo i dalje opravdano, posto je ovaj deo pisan pre PathQueue i veceg dela Paths
        for(int i=0; i<links.size(); i++) { //n^2, ali n je u vecini slucajeva <20, pa mislim da nece previse uticati
            for(int j=0; j<i; j++)
                if(links.get(i).toStation.equals(links.get(j).toStation) && links.get(j).usingLine.isWalking())
                    Collections.swap(links, i, j); //correction, linije racunaju smer nekoliko stanica unapred
        }
        iterators.put(line, 0);
        sortedLinks.put(line, links);
    }

    /**
     * @return true ako je linija poznata stanici i njen iterator je manji od broja linkova
     */
    public boolean hasNext(Line line) {
        return !iterators.containsKey(line) || iterators.get(line) < links.size();
    }

    /**
     * Vraca najmanju tezinu do sledece stanice ili {@link Double#POSITIVE_INFINITY} ako takva ne postoji, ne
     * povecavajuci iterator
     */
    public double peekNextWeight(Line line) {
        if(!iterators.containsKey(line)) setEstimates(line);

        int iterator = iterators.get(line);
        if(iterator < links.size()) {
            Link next = sortedLinks.get(line).get(iterator);
            return next.calculateWeightFor(line);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Vraca sledeci link s najmanjom tezinom za datu liniju (koristi se zbog presedanja i koeficijenta za tip prevoza)
     * @param line data linija
     */
    public Link getNextBestGuess(Line line) {
        if(!iterators.containsKey(line)) setEstimates(line);

        int iterator = iterators.get(line);
        if(iterator < links.size()) {
            Link theChosenOne = sortedLinks.get(line).get(iterator);
            iterators.replace(line, iterator+1);
            return theChosenOne;
        } else {
            return null;
        }
    }

    /**
     * Vraca listu linkova, pocinjuci od onoga sa najmanjom tezinom, cije tezine su dovoljno bliske da budu dobri putevi.
     * "Dovoljno bliske" je definisano sa {@link #DELTA_WEIGHT}
     * @see #DELTA_WEIGHT
     */
    public List<Link> getNextBestGuesses(Line line) {
        List<Link> guesses = new LinkedList<>();
            if(line.isInitial()) { //ako je pocetna linija, procene nemaju toliko smisla, tako da vracam sve moguce
            Link l; //ima veze s nacinom na koji Paths radi (root nema efikasnost), pa ovo mozda nije optimalna metoda
            while((l=getNextBestGuess(line))!=null) //za slucaj da opet promenim stablo (ili zamenim s nekom drugom strukturom)
                guesses.add(l); //racunam da necu imati vremena da menjam ponovo, tako da ovo ostaje samo mali coupling issue (u teoriji)
            return guesses;
        }
        double nextWeight = peekNextWeight(line);
        if(Double.isInfinite(nextWeight)) return guesses;
        guesses.add(getNextBestGuess(line));
        Link next;
        while(peekNextWeight(line) - DELTA_WEIGHT < nextWeight && (next=getNextBestGuess(line))!=null) {
            guesses.add(next);
        }
        return guesses;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Station && id == ((Station)obj).id)
                || obj instanceof Integer && obj.equals(id); //prihvatam i Integer, zbog mape
    }

    @Override
    public int hashCode() {
        return id;
    }

    public LatLng getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public String toMachineString() {
        StringBuilder builder = new StringBuilder();
        builder.append(id).append(BaseIO.FIELD_SEPARATOR).append(name).append(BaseIO.FIELD_SEPARATOR)
                .append(location.toMachineString()).append(BaseIO.FIELD_SEPARATOR);
        for(Link l : links) {
            builder.append(l.toMachineString()).append(BaseIO.ARRAY_SEPARATOR);
        }
        return builder.toString();
    }

    /**
     * @return ime stanice
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Note to self: Line-agnostic, station-specific
     * Predstavlja vezu izmedju dve stanice. Odredjen je stanicom i linijom.
     * Terminologija: cost ili trosak grubo odgovara utrosenom vremenu, uzima u obzir udaljenost, tip prevoza i presedanja
     *                weight ili tezina odredjuje optimalnu liniju 'na duze staze', uzima u obzir smer kretanja i
     *                obliznje 'skupe' stanice
     */
    public class Link {
        /**
         * Dodato jedan dan pre nego sto sam predao, pa je samim tim potrebno jos istestirati.
         * Ako je true, trosak ulazi u tezinu. Ovo favorizuje blize linkove, sto cesto nije poenta tezine, koja me
         * uglavnom zanima 'na duze staze'. Posto je dodato dosta nakon sto sam odredio metode racunanja oba, moguce je
         * da sam nesto prevideo. Takodje, brojevi koji se ticu smera i presedanja (tj. glavnih komponenata tezine) su
         * vec bili podeseni daleko iznad onih koji ulaze u trosak, kako bi trosak mogao da bude zanemarljiv. Kada to
         * nije vise problem, mozda ce trebati opet da se podese na 'normalnije' vrednosti.
         */
        private static final boolean USE_COST_FOR_WEIGHT = false;

        public static final int NEARBY_EXPENSIVE_SPOTS_WEIGHT = 500;
        public static final double WALK_EXPENSIVE_SPOTS_APPROX = 1.5;
        public static final int SWITCH_COST = 1000; //imati na umu da se ovo mnozi razlikom koeficijenata + Line#COST_WALK
        public static final int STOP_COST = 100;
        public static final double WALK_BEARING_COEFFICIENT = 1.5;//smer setanja do stanice je uvek tacan, dok se linije
                                                                  //oslanjaju na smer u odnosu nekoliko stanica unapred

        private Station toStation;
        private Line usingLine;
        private double initialWeight;
        private double cost;
        private double bearingDiff;
        private double bearingWeight;
        private String machineStringForProcessing; //Stanice se ucitavaju pre linije, pa linije ne postoje u trenutku
        //pozivanja konstruktora za Link. Ako se ucitava iz fajla, podaci za Linkove se moraju ucitati na kraju
        //(tj. kada Linije vec postoje u Bazi)

        private Link(String machineString) {
            machineStringForProcessing = machineString;
        }

        /**
         * Pozvati tek nakon sto se linije ucitaju
         */
        private void loadDataFromString() {
            String[] fields = machineStringForProcessing.split(BaseIO.MINOR_SEPARATOR);
            toStation = Base.getInstance().getStation(Integer.parseInt(fields[0]));
            usingLine = Base.getInstance().getLine(fields[1]);
            if(usingLine == null) throw new NullPointerException(); //please forgive me
            initialWeight = Double.parseDouble(fields[2]);
            cost = Double.parseDouble(fields[3]);
            machineStringForProcessing = null;
        }

        private Link(Station destination, Line usingLine) {
            this.toStation = destination;
            this.usingLine = usingLine;
            cost = Station.this.getLocation().costTo(toStation.getLocation(), usingLine);
            if(!usingLine.isWalking())
                initialWeight = (USE_COST_FOR_WEIGHT ? cost : 0)
                        + usingLine.getNumberOfNearbyExpensiveStations(Station.this) * NEARBY_EXPENSIVE_SPOTS_WEIGHT;
            else
                if(toStation.getLocation().isNearBusySpot())
                    initialWeight = (USE_COST_FOR_WEIGHT ? cost : 0)
                            + WALK_EXPENSIVE_SPOTS_APPROX * NEARBY_EXPENSIVE_SPOTS_WEIGHT;
                else
                    initialWeight = (USE_COST_FOR_WEIGHT ? cost : 0) ;
        }

        public Station getToStation() {
            return toStation;
        }

        public Line getUsingLine() {
            return usingLine;
        }

        /**
         * Svodi ugao na vrednost izmedju 0 i 180 stepeni i postavlja odgovarajuce polje
         * @param bearingDiff ugao izmedju pravca linije koja povezuje pocetak i cilj i pravca ove transportne linije
         */
        private void setBearingDiff(double bearingDiff) {
            bearingDiff = Math.abs(bearingDiff);
            if(bearingDiff > 180)
                bearingDiff = 360-bearingDiff;
            this.bearingDiff = bearingDiff;
            this.bearingWeight = selectBearingWeight();
        }

        /**
         * Utility, vraca 'tezinu' za dati ugao
         */
        private double selectBearingWeight() {
            double distanceToGoal = toStation.getLocation().distanceBetween(Base.getInstance().getGoal().getLocation());
            for(int i=0; i<BEARING_LEVELS.length; i++)
                if(bearingDiff < BEARING_LEVELS[i])
                    return BEARING_WEIGHTS[i] * (distanceToGoal/1000);
            return BEARING_WEIGHTS[BEARING_WEIGHTS.length-1] * (distanceToGoal/1000);
        }

        private double calculateWeightFor(Line l) {
            return initialWeight + bearingWeight * (l.isWalking() ? WALK_BEARING_COEFFICIENT : 1)
                    + getStationCost(l); //todo videti da li je kondicionalni operator zaista neophodan svuda
        }

        /**
         * Vraca koliko stanica 'kosta', tj presedanje ili default vrednost (STOP_COST), pomnozen koeficijentom tipa
         * @param l linija kojom se dolazi do ove stanice
         * @see #SWITCH_COST
         * @see #STOP_COST
         */
        private double getStationCost(Line l) {
            double stationCost;
            if(l.isInitial()) stationCost = 0; //ako je prva
            else if(usingLine.equals(l) && !l.isWalking()) { //ako nastavlja istom
                stationCost = STOP_COST * l.typeCostCoefficient();
            } else { //ako preseda (ili seta)
                stationCost = SWITCH_COST*(Line.COST_WALK +l.typeCostCoefficient()-usingLine.typeCostCoefficient());
            }
            return stationCost;
        }

        /**
         * Vraca trosak (cenu) od trenutne stanice (spoljne klase) do stanice do koje vodi ova veza
         * @param l linija kojom se dolazi do trenutne stanice
         * @return cena ove veze koristeci datu liniju
         */
        public double calculateCostFor(Line l) {
            assert cost + getStationCost(l) > 0; //happened
            return cost + getStationCost(l);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Link) {
                Link other = (Link)obj;
                return toStation.equals(other.toStation) && Objects.equals(usingLine, other.usingLine);
            } else return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(toStation, usingLine);
        }

        private String toMachineString() {
            return toStation.getId() + BaseIO.MINOR_SEPARATOR + usingLine.getId() + BaseIO.MINOR_SEPARATOR
                    + initialWeight + BaseIO.MINOR_SEPARATOR + cost;
        }
    }
}
