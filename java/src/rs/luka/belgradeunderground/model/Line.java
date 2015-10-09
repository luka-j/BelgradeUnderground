package rs.luka.belgradeunderground.model;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.data.Base;
import rs.luka.belgradeunderground.io.BaseIO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Created by luka on 14.9.15.
 * Serialization: id/type/smerA0,smerA1,.../smerB0,smerB1,.../directions0,directions1,...
 */
public class Line {
    //specijalni slucajevi:
    private static final Line initial = new Line("initial", "tram", new LinkedList<>(), new LinkedList<>());
    private static final Line walking = new Line("walk", "walk", new LinkedList<>(), new LinkedList<>()); //lakse je nego null

    public static final int TYPE_BUS = 0;
    public static final int TYPE_TRAM = 1;
    public static final int TYPE_TROLLEY = 2;
    public static final int TYPE_WALK = 3;

    /*
     * Koeficijenti za razlicite tipove prevoza
     */
    public static final double COST_TRAM = 1;
    public static final double COST_TROLLEY = 1.2;
    public static final double COST_BUS = 1.5;
    public static final double COST_WALK = 4;

    public static final int EXPENSIVE_STATIONS_LOOKAHEAD = 6;
    public static final int DIRECTION_LOOKAHEAD = 5;  //ako je Config.USE_PRECISE_DIRECTIONS true
    public static final int DIRECTION_ESTIMATES = 12; //ako je Config.USE_PRECISE_DIRECTIONS false
    private final double[] directionsA;
    private final double[] directionsB;

    private String id;
    private int type;
    private List<Station> smerA = new ArrayList<>();
    private List<Station> smerB = new ArrayList<>();

    /**
     * Verovatno najkompleksniji konstruktor. Veliki deo se odnosi na podesavanje smera, izdeljen u tri metode
     * (originalno je sve bilo u konstruktoru. God forbid.)
     * @param id id linije (broj, opciono uz 'L', 'P', 'E' ili sl.). Sto se programa tice, svejedno je, mogu biti i reci
     * @param type tip linije, jedno od "bus", "tram" i "trolleybus"
     * @param smerAIds id-ovi stanica u smeru A
     * @param smerBIds id-ovi stanica u smeru B
     */
    public Line(String id, String type, List<Integer> smerAIds, List<Integer> smerBIds) {
        this.id = id;
        switch (type) {
            case "bus": this.type = TYPE_BUS;
                break;
            case "tram": this.type = TYPE_TRAM;
                break;
            case "trolleybus": this.type = TYPE_TROLLEY;
                break;
            case "walk": this.type = TYPE_WALK;
                break; //koristi se samo za specijalnu liniju walking
            default: throw new IllegalArgumentException("Invalid type: " + type);
        }

        if(smerAIds!=null && smerBIds != null && (smerAIds.size() > 0 || smerBIds.size() > 0)) {
            smerA.addAll(smerAIds.stream().map((Integer smerId) -> Base.getInstance().getStation(smerId)).collect(Collectors.toList()));
            smerB.addAll(smerBIds.stream().map((Integer smerId) -> Base.getInstance().getStation(smerId)).collect(Collectors.toList()));
            //so far so good

            if(Config.USE_PRECISE_DIRECTIONS) { //ako koristim 'precizne' smerove, tj. za svaku stanicu gledam
                                                //DIRECTION_LOOKAHEAD stanica unapred
                directionsA = new double[smerA.size()];
                directionsB = new double[smerB.size()];
                if(Config.USE_AVERAGE_BEARING_DIRECTIONS) {
                    setDirectionsAverage();
                } else {
                    setDirectionsSimple();
                }
            } else {
                directionsA = new double[DIRECTION_ESTIMATES];
                directionsB = new double[DIRECTION_ESTIMATES];
                setDirectionsPartial();
            }
        } else {
            directionsA = new double[0];
            directionsB = new double[0];
        }
    }

    /**
     * Deli liniju na DIRECTION_ESTIMATES delova i racuna smer za svaki slicno kao u setDirectionsSimple
     */
    private void setDirectionsPartial() {
        for (int i = 0; i < DIRECTION_ESTIMATES; i++) {
            directionsA[i] = smerA.get((i / DIRECTION_ESTIMATES) * smerA.size()).getLocation()
                    .bearingTo(smerA.get(((i + 1) / DIRECTION_ESTIMATES)).getLocation());
            if (smerB.size() > 0)
                directionsB[i] = smerB.get((i / DIRECTION_ESTIMATES) * smerB.size()).getLocation()
                        .bearingTo(smerB.get(((i + 1) / DIRECTION_ESTIMATES)).getLocation());
        }
    }

    /**
     * Odredjuje smer linije koja povezuje i-tu i (i+DIRECTION_LOOKAHEAD)-tu stanicu
     */
    private void setDirectionsSimple() {
        //uzimam ovu i (ovu+DIRECTION_LOOKAHEAD)tu stanicu i odredjujem ugao prema toj liniji
        for (int i = 0; i < smerA.size() - DIRECTION_LOOKAHEAD; i++)
            directionsA[i] = smerA.get(i).getLocation().bearingTo(smerA.get(i + DIRECTION_LOOKAHEAD).getLocation());
        for (int i = (smerA.size() < DIRECTION_LOOKAHEAD ? 0 : smerA.size() - DIRECTION_LOOKAHEAD); i < smerA.size(); i++)
            //jer postoje linije poput 38 sa izuzetno malim brojem stajalista
            directionsA[i] = smerA.get(i).getLocation().bearingTo(smerA.get(smerA.size() - 1).getLocation());
        for (int i = 0; i < smerB.size() - DIRECTION_LOOKAHEAD; i++)
            directionsB[i] = smerB.get(i).getLocation().bearingTo(smerB.get(i + DIRECTION_LOOKAHEAD).getLocation());
        for (int i = (smerB.size() < DIRECTION_LOOKAHEAD ? 0 : smerB.size() - DIRECTION_LOOKAHEAD); i < smerB.size(); i++)
            directionsB[i] = smerB.get(i).getLocation().bearingTo(smerB.get(smerB.size() - 1).getLocation());
    }

    /**
     * Odredjuje prosek vrednosti smerova svih stanica izmedju i-te i (i+DIRECTION_LOOKAHEAD)-te
     */
    private void setDirectionsAverage() {
        //pisano oko 1 ujutru. Neke stvari ne moraju nuzno da imaju smisla. Ili sve. Please forgive me
        //uzimam prosek smerova (uglova) od ove, pa DIRECTION_LOOKAHEAD unapred
        double sum=0;
        double[] oneAheadA = new double[smerA.size()], oneAheadB = new double[smerB.size()];
        for(int i=0; i<smerA.size()-1; i++) oneAheadA[i] = smerA.get(i).getLocation().bearingTo(smerA.get(i+1).getLocation());
        for(int i=0; i<smerB.size()-1; i++) oneAheadB[i] = smerB.get(i).getLocation().bearingTo(smerB.get(i+1).getLocation());

        for(int i=0; i<DIRECTION_LOOKAHEAD-1 && i<smerA.size()-1; i++) sum+=oneAheadA[i];
        if(DIRECTION_LOOKAHEAD > smerA.size()) {
            for(int i=0; i<smerA.size(); i++) {
                directionsA[i] = sum/(smerA.size()-i);
                sum-=oneAheadA[i];
            }
        } else {
            for(int i=0; i<smerA.size()-DIRECTION_LOOKAHEAD; i++) {
                directionsA[i] = sum/DIRECTION_LOOKAHEAD;
                sum+=oneAheadA[i+DIRECTION_LOOKAHEAD];
                sum-=oneAheadA[i];
            }
            for(int i=DIRECTION_LOOKAHEAD; i<smerA.size(); i++) {
                directionsA[i] = sum/(smerA.size()-i);
                sum-=oneAheadA[i];
            }
        }
        sum=0;
        for(int i=0; i<DIRECTION_LOOKAHEAD-1 && i<smerB.size()-1; i++) sum+=oneAheadB[i];
        if(DIRECTION_LOOKAHEAD > smerB.size()) {
            for(int i=0; i<smerB.size(); i++) {
                directionsA[i] = sum/(smerB.size()-i);
                sum-=oneAheadB[i];
            }
        } else {
            for(int i=0; i<smerB.size()-DIRECTION_LOOKAHEAD; i++) {
                directionsB[i] = sum/DIRECTION_LOOKAHEAD;
                sum+=oneAheadB[i+DIRECTION_LOOKAHEAD];
                sum-=oneAheadB[i];
            }
            for(int i=DIRECTION_LOOKAHEAD; i<smerB.size(); i++) {
                directionsB[i] = sum/(smerB.size()-i);
                sum-=oneAheadB[i];
            }
        }
        //todo note to self: ne pisati kod kasno u noć
    }

    /**
     * Konstruktor koji prima string generisan od strane {@link #toMachineReadableString()}
     * @param machineReadableString
     */
    public Line(String machineReadableString) {
        String[] fields = machineReadableString.split(BaseIO.FIELD_SEPARATOR);
        id = fields[0];
        type = Integer.parseInt(fields[1]);
        List<String> smerAIds = Arrays.asList(fields[2].split(BaseIO.ARRAY_SEPARATOR));
        List<String> smerBIds = Arrays.asList(fields[3].split(BaseIO.ARRAY_SEPARATOR));
        Base base = Base.getInstance();
        smerA.addAll(smerAIds.stream().map(Integer::parseInt).map(base::getStation).collect(Collectors.toList()));
        if(!smerBIds.isEmpty() && !smerBIds.get(0).isEmpty())
        //neke linije se vode kao kruzne i ne postoji smerB, dok 405L fali u fajlovima, v. Station#Station(String)
            smerB.addAll(smerBIds.stream().map(Integer::parseInt).map(base::getStation).collect(Collectors.toList()));
        String[] directionBStrs;
        String[] directionAStrs = fields[4].split(BaseIO.ARRAY_SEPARATOR);
        if(fields.length > 5)
            directionBStrs = fields[5].split(BaseIO.ARRAY_SEPARATOR);
        else
            directionBStrs = new String[0];
        directionsA = new double[directionAStrs.length];
        directionsB = new double[directionBStrs.length];
        for(int i=0; i<directionAStrs.length; i++) {
            directionsA[i] = Double.parseDouble(directionAStrs[i]);
        }
        for(int i=0; i<directionBStrs.length; i++) {
            directionsB[i] = Double.parseDouble(directionBStrs[i]);
        }
    }

    /*
     * Specijalni slucajevi
     */
    public static Line getInitial() {
        return initial;
    }
    public static Line getWalking() {
        return walking;
    }
    public boolean isInitial() {
        return this == initial;
    }
    public boolean isWalking() {
        return this == walking;
    }

    public static Line getSpecial(String id) {
        if(initial.getId().equals(id))
            return initial;
        if(walking.getId().equals(id))
            return walking;
        return null;
    }

    /**
     * Mapira tip prevoza u koeficijent
     */
    public double typeCostCoefficient() {
        switch (type) {
            case TYPE_BUS:
                return COST_BUS;
            case TYPE_TRAM:
                return COST_TRAM;
            case TYPE_TROLLEY:
                return COST_TROLLEY;
            case TYPE_WALK:
                return COST_WALK;
            default: throw new IllegalArgumentException("Wrong type: " + type);
        }
    }

    /**
     * Vraca smer u kojem se linija krece od date stanice. Rezultat zavisi od {@link Config#USE_PRECISE_DIRECTIONS}
     * @param s
     * @return
     */
    public double getDirectionAt(Station s) {
        if(smerA == null || smerA.size() == 0) return 0;
        int index = smerA.indexOf(s);
        if (index >= 0) {
            return directionsA[Config.USE_PRECISE_DIRECTIONS ?
                                index :
                                ((int) (((float) index / smerA.size()) * DIRECTION_ESTIMATES))];
        } else {
            index = smerB.indexOf(s);
            return directionsB[Config.USE_PRECISE_DIRECTIONS ?
                                index :
                                (int) (((float) index / smerB.size()) * DIRECTION_ESTIMATES)];
        }
    }

    /**
     * Dodaje (registruje) ovu liniju stanicama na kojima staje
     * @return
     */
    public Line registerLineToStations() {
        smerA.forEach((Station s) -> s.addLine(this));
        smerB.forEach((Station s) -> s.addLine(this));
        return this;
    }

    public String getId() {
        return id;
    }

    /**
     * Lista, jer neki autobusi koriste istu stanicu kao okretnicu. Ocekujem da su takvi slucajevi retki.
     * @param s
     * @return
     */
    public List<Station> getStationAfter(Station s) {
        List<Station> ret = new LinkedList<>();
        if(smerA == null || smerA.size() == 0) return null;
        int indexA = smerA.indexOf(s);
        int indexB = smerB.indexOf(s);
        if(indexA >= 0) {
            if(indexA + 1 < smerA.size())
                ret.add(smerA.get(indexA + 1));
            else if(isCircular()) //hvala geomasteru, ne bih se ovoga sam setio
                ret.add(smerA.get(0));
        }
        if(indexB >= 0) {
            if(indexB + 1 < smerB.size())
                ret.add(smerB.get(indexB + 1));
        }
        return ret;
    }

    /**
     * Vraca broj obliznjih 'skupih' stanica (stanica koje su {@link LatLng#isExpensive}, tj. nalaze se u blizini
     * mesta na kojima je poznato da ce se prevoz kretati sporije nego obicno).
     * @param s
     * @return
     */
    public int getNumberOfNearbyExpensiveStations(Station s) {
        if(smerA == null || smerA.size() == 0) return 0;
        int index = smerA.indexOf(s), expensive=0;
        if(index >= 0) {
            for(int i=0; i< EXPENSIVE_STATIONS_LOOKAHEAD && index + i < smerA.size(); i++)
                if(smerA.get(index+i).getLocation().isNearBusySpot())
                    expensive++;
            return expensive;
        } else {
            index = smerB.indexOf(s);
            for(int i=0; i< EXPENSIVE_STATIONS_LOOKAHEAD && index + i < smerB.size(); i++)
                if(smerB.get(index+i).getLocation().isNearBusySpot())
                    expensive++;
            return expensive;
        }
    }

    public boolean isCircular() {
        return smerB.isEmpty() && !isWalking() && !isInitial();
        //pretpostavljam da su linije koje nemaju drugi smer kruzne (tom logikom 2 nije kruzna,
        //ali racunam da su slucajevi kada je prelazenje 'preko' okretnice optimalni retki.
    }
    //----------------overrideovi----------------------------------

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Line && id.equals(((Line) obj).id))
                || (obj instanceof String && id.equals(obj)); //prihvatam i String, kako bih iz mape u bazi mogao da
                                                              //izvucem liniju samo po njenom id-u
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Za serijalizaciju
     * @return
     * @see BaseIO
     */
    public String toMachineReadableString() {
        StringBuilder builder = new StringBuilder();
        builder.append(id).append(BaseIO.FIELD_SEPARATOR).append(type).append(BaseIO.FIELD_SEPARATOR);
        for(Station s : smerA) {
            builder.append(s.getId()).append(BaseIO.ARRAY_SEPARATOR);
        }
        builder.append(BaseIO.FIELD_SEPARATOR);
        for(Station s : smerB) {
            builder.append(s.getId()).append(BaseIO.ARRAY_SEPARATOR);
        }
        builder.append(BaseIO.FIELD_SEPARATOR);
        for(double d : directionsA)
            builder.append(d).append(BaseIO.ARRAY_SEPARATOR);
        builder.append(BaseIO.FIELD_SEPARATOR);
        for(double d : directionsB)
            builder.append(d).append(BaseIO.ARRAY_SEPARATOR);
        return builder.toString();
    }

    @Override
    public String toString() {
        return id;
    }


    //Neke od prvih linija koda za program, ostavljam cisto da se setim originalne ideje (kako je nikada ne bih ponovio)
    /*
    /**
     * Pretpostavlja da pozivalac zna u kom smeru se krece (inace je unspecified behaviour)
     * @param
     * @param
     */
    /*
    public void hopOn(int smer, Station s) {
        switch (smer) {
            case SMER_A:
                iterator = SMER_A * smerA.indexOf(s);
                break;
            case SMER_B:
                iterator = SMER_B * smerB.indexOf(s);
                break;
            case SMER_AUTO:
                iterator = SMER_A * smerA.indexOf(s);
                if(iterator < 0) {
                    iterator = SMER_B * smerB.indexOf(s);
                }
                break;
            default: throw new IllegalArgumentException("Pogresan smer: " + smer);
        }
    }

    public void hopOff() {
        iterator = -1;
    }

    /**
     * Prelazi na sledecu stanicu i vraca cenu
     * @return
     */
    /*
    public double goToNext() {
        if(iterator * SMER_A > 0) {
            Station current = smerA.get(iterator/SMER_A);
            iterator += SMER_A;
            if(iterator/SMER_A >= smerA.size() || iterator/SMER_A < 0) {
                hopOff();
                return -1;
            }
            Station next = smerA.get(iterator/SMER_A);
            return current.getLocation().costTo(next.getLocation(), this) + STOP_COST * typeCostCoefficient();
        } else {
            Station current = smerB.get(iterator/SMER_B);
            iterator += SMER_B;
            if(iterator/SMER_B >= smerB.size() || iterator/SMER_B < 0) {
                hopOff();
                return -1;
            }
            Station next = smerB.get(iterator/SMER_B);
            return current.getLocation().costTo(next.getLocation(), this) + STOP_COST * typeCostCoefficient();
        }
    }

    public Station peekNext() {
        if(iterator * SMER_A > 0) {
            return smerA.get(iterator / SMER_A);
        } else {
            return smerB.get(iterator / SMER_B);
        }
    }
    */
}
