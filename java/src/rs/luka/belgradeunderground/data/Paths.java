package rs.luka.belgradeunderground.data;

import com.sun.istack.internal.NotNull;
import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.io.LogUtils;
import rs.luka.belgradeunderground.io.UserIO;
import rs.luka.belgradeunderground.model.Line;
import rs.luka.belgradeunderground.model.Station;

import javax.swing.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * v3.5
 * Struktura koja cuva moguce putanje do nekog trenutka. Stablo koje se grana u zavisnosti od potencijalno optimalne dece.
 * Meri efikasnost putanje kao odnos pravolinijskog rastojanja od pocetka do trenutne lokacije i troska puta (cost) i
 * kada ono ode ispod definisane vrednosti definisani broj puta, vraca se na node koji ima najvecu efikasnost. Ponavlja
 * dok ne dodje do cilja. Mana mu je da poslednjih MAX_EFFICIENCY_FAILS node-ova nece biti optimalni.
 *
 * Kratak istorijat:
 * v1: Prva verzija ovoga je bilo nesto sa listama, ni ne secam se vise. U svakom slucaju, ne znam kako mi je palo na
 * pamet, posto uopste nije imalo smisla. Od toga sam brzo odustao.
 * v2: iliti PathTree, nesto ovakvo. Koristio je listu za nodes, tzv. 'snapshots' i merio je efikasnost svakih n puta.
 * Glavna razlika je sto je uzimao samo po jedno dete i ubacivao u children (Station#getNextBestGuess), umesto nekoliko
 * najboljih
 * v2.5: Izmenjen PathTree tako da za rollback (kada je efikasnost premala ili dodje do poslednje stanice) radi traversal
 * i izbacena sva static polja tako da u aplikaciji mogu da postoje vise stabala u isto vreme.
 * v3: iliti Paths, ovo samo umesto nodes svaki clan cuva optimalAncestor-a do tog trenutka. Iz nekog razloga sam osecao
 * da je dobra ideja da to bude referenca na Paths (wrapper), pa kada je promenim na jednom mestu, menja se svuda gde
 * postoji. I dalje smatram da je to bolji dizajn, ali  tezi da se uradi kako treba. S obzirom da mi ponestaje vremena
 * treba mi jednostavnije resenje.
 * v3.5: iliti trenutno stanje. v3 izmenjen da koristi PriorityQueue za smestanje svih objekata. Potencijalno ogromne
 * velicine i dupliciranje podataka opravdavam cinjenicom da cu na racunaru uglavnom imati viska memorije
 * v4: PathQueue
 * Note: v3 je u stvari USE_NODE_QUEUE = false. S obzirom da sam poceo nesto da menjam i ostavio nedovrseno, nece funkcionisati
 *
 * Created by luka on 3.10.15.
 * @see Station#getNextBestGuesses(Line)
 */
public class Paths implements Comparable<Paths> {
    public static final double EFFICIENCY_EMPTY = 0;
    private static final boolean USE_NODE_QUEUE = true; //necessary evil
    //za USE_NODE_QUEUE = false algoritam je nedovrsen, netestiran i gotovo sigurno ima gresaka. Use at your own responsibility
    private static final Logger LOG = Logger.getLogger(Paths.class.getName());
    private static final int NODES_INITIAL_SIZE = 384; //mozda treba i vise
    //PriorityQueue posto me zanima samo prvi, a potrebno je da uvek budu sortirani
    private static final PriorityQueue<Paths> nodes = new PriorityQueue<>(NODES_INITIAL_SIZE); //koriscenje heapa zajedno sa ovim stablom ocigledno nije optimalno, ali s obzirom da bolje ne stignem
                                                                                               //videti PathQueue
    /**
     * Za slucaj da je efikasnost node-a sa najvecom efikasnoscu i dalje manja od minimalne, koliko puta treba smanjiti
     * minimalnu efikasnost
     */
    private static double REDUCE_EFFICIENCY_FACTOR = 1.1;
    /**
     * Koliko puta efikasnost moze da bude manja od minimalne pre nego sto se vraca do nekog pretka umesto nastavlja
     * na dete
     */
    private static int MAX_EFFICIENCY_FAILS = 2;
    /**
     * Minimalna efikasnost
     */
    private static double minEfficiency = 0.6;
    private static int efficiencyFails = 0; //counter

    private final Paths parent;
    private final List<Paths> children = new LinkedList<>();
    private final Station currentStation;
    private final Line currentLine;
    private final int level; //debugging purposes
    private final double cost;
    private final double efficiency;
    private int nextChild = 0; //iterator
    private Reference optimalAncestor; //Prvobitno je bio ancestor, sada prihvata i siblings. Nekorisceno zbog nodes.

    /**
     * Kopira parametre i racuna efikasnost. Ocekuje da se spolja postavi optimalAncestor.
     */
    private Paths(Paths parent, Station station, Line line, double cost) {
        this.parent = parent;
        this.currentStation = station;
        this.currentLine = line;
        this.level = (parent == null ? 0 : parent.level + 1);
        this.cost = cost;
        this.efficiency = Base.getInstance().getStart().getLocation().distanceBetween(currentStation.getLocation())/cost;
    }

    /**
     * Inicijalizuje stablo s korenom u start. Parametri se ticu efikasnosti, tj. nacin procenjivanja kada je ona premala.
     * Primetio sam da odabrana ruta uglavnom zavisi od ova tri broja, pa zato dozvoljavam da se podese spolja.
     * U programu nikada ne smeju da se koriste vise stabala istovremeno, jer ova metoda brise ostatke prethodnog (!)
     * @param start pocetna stanica
     * @param minEfficiency minimalna efikasnost
     * @param maxFails broj koliko puta efikasnost sme da bude ispod minimalne pre odabira drugog puta
     * @param reduceFactor broj kojim je minimalna efikasnost deli pod uslovom da ne postoji node sa efikasnoscu manjom
     *                     od minEfficiency
     * @return koren stabla
     */
    public static Paths init(Station start, double minEfficiency, int maxFails, double reduceFactor) {
        if(Config.DEBUG) {
            LOG.setLevel(Level.FINER);
            Handler h = LogUtils.getDefaultFileHandler();
            if(h!=null)
                LOG.addHandler(h);
        }
        nodes.clear();
        efficiencyFails = 0;
        Paths.minEfficiency = minEfficiency;
        MAX_EFFICIENCY_FAILS = maxFails;
        REDUCE_EFFICIENCY_FACTOR = reduceFactor;
        System.gc();
        return new Paths(null, start, Line.getInitial(), 0.01);
    }

    /**
     * @deprecated
     * Hermafrodit je, dakle zanimaju ga roditelji i deca
     * Note to self: objekat ne moze sam sebi da bude optimalAncestor (!!), dovodi do ciklicnog ponasanja
     * @return
     */
    private Paths assessCloseFamily() { //todo fix mess w/ refs
        if(parent == null) return null; //ne bi trebalo da se ikada vraca na initial, jer ne postoji nacin da mu utvrdim efikasnost
        Paths optimal;
        if(parent.optimalAncestor == null) {
            optimal = parent.children.get(0);
            if(optimal == this) optimal = parent.children.get(1);
        } else {
            optimal = parent;
        }
        for(Paths p : parent.children) {
            if(p != this && !p.isDeadEnd() && p.efficiency != EFFICIENCY_EMPTY &&  p.efficiency > optimal.efficiency) {
                optimal = p;
            }
        }

        LOG.finer("Pronasao optimal za dete: " + optimal);
        return optimal;
    }

    /**
     * @deprecated
     * @return
     */
    private Reference getOptimalReference() {
        Paths optimal = assessCloseFamily();
        if(optimalAncestor == null) return new Reference(optimal);
        if(optimal == null) return null;
        if(optimal.equals(optimalAncestor.obj)) {
            LOG.finer("optimal je isti kao roditeljev, vracam taj");
            return optimalAncestor;
        } else {
            LOG.finer("Novi optimal, pravim referencu");
            return new Reference(optimal);
        }
    }

    /**
     * Dodaje datu listu u decu. U zavisnosti da li je {@link #USE_NODE_QUEUE} postavljen na true, dodaje decu i u
     * {@link #nodes} ili im racuna optimalnog rodjaka za vracanje.
     * @param paths
     */
    private void addChildren(List<Paths> paths) {
        if (paths.size()==0) return;
        children.addAll(paths);
        if(USE_NODE_QUEUE)
            nodes.addAll(paths);
        else {
            if (nextChild == 0) {
                if (optimalAncestor == null)
                    children.get(0).optimalAncestor = new Reference(children.get(1));
                else if (children.size() == 1) { //nemoguce je da je optimalAncestor null i children.size()==1 u isto vreme
                    //ja se nadam. Ako se ispostavi da je moguce, povecati max walking distance
                    children.get(0).optimalAncestor = optimalAncestor;
                } else {
                    children.get(0).optimalAncestor = optimalAncestor.obj.efficiency > children.get(1).efficiency ?
                            optimalAncestor : new Reference(children.get(1));
                }
            }
            if (children.size() == 1) return;
            Paths optimalChild = children.get(0), secondOptimal = children.get(1);
            if (children.get(0).efficiency < children.get(1).efficiency) {
                Paths temp = optimalChild;
                optimalChild = secondOptimal;
                secondOptimal = temp;
            }
            for (Paths child : children) {
                if (child.efficiency > optimalChild.efficiency) {
                    Paths temp = optimalChild;
                    optimalChild = child;
                    secondOptimal = temp;
                } else if (child.efficiency > secondOptimal.efficiency) {
                    secondOptimal = child;
                }
            }
            if (optimalAncestor == null || secondOptimal.efficiency > optimalAncestor.obj.efficiency) {
                optimalChild.optimalAncestor = new Reference(secondOptimal);
            } else {
                optimalChild.optimalAncestor = optimalAncestor;
            }
            if (optimalAncestor == null || optimalChild.efficiency > optimalAncestor.obj.efficiency) {
                for (Paths child : children) {
                    if (!child.equals(optimalChild))
                        child.optimalAncestor = new Reference(optimalChild);
                }
                int debug = 1; //debugging purposes (breakpoint)
            } else {
                for (Paths child : children) {
                    if (!child.equals(optimalChild))
                        child.optimalAncestor = optimalAncestor;
                }
                int debug = 1; //debugging purposes (breakpoint)
            }
        }
    }

    /**
     * Uzima moguce naredne clanove (decu), procenjuje efikasnost i po potrebi se vraca na bolji izbor, ako deca nisu
     * odgovarajuca.
     * @return sledeći član. Uglavnom dete ovog, mada može biti i sestrić. Ili roditelj. Ili dalji rođak.
     * @see #testEfficiency()
     * @see #addChildren(List)
     * @see Station#getNextBestGuesses(Line)
     */
    public Paths getNext() {
        if(!testEfficiency()) {
            if (USE_NODE_QUEUE) {
                Paths optimal = rollback();
                if(optimal.efficiency < minEfficiency) minEfficiency/=REDUCE_EFFICIENCY_FACTOR;
                LOG.fine("Efikasnost (" + efficiency + ") premala, vracam se na optimal: " + optimal);
                return optimal;
            } else {
                LOG.fine("Efikasnost (" + efficiency + ") premala, vracam se na optimal: " + optimalAncestor.obj);
                return optimalAncestor.obj;
            }
        }

        if(nextChild == children.size()) {
            List<Paths> bestGuesses = currentStation.getNextBestGuesses(currentLine).stream().map((Station.Link l) ->
                    new Paths(this, l.getToStation(), l.getUsingLine(), cost + l.calculateCostFor(currentLine))).collect(Collectors.toList());
            bestGuesses.sort(null); //sortiram po efikasnosti, posto su im tezine blizu
            addChildren(bestGuesses);
        }
        if(nextChild == children.size()) { //ako su velicine i dalje iste, znaci da nije nista dodao,
                                           //tj. nema vise mogucih izbora i postaje dead end
            if (USE_NODE_QUEUE) {
                Paths optimal = rollback();
                LOG.fine("Dead end; vracam se na optimal: " + optimal);
                return optimal;
            } else {
                if (optimalAncestor.obj.isDeadEnd())
                    cutoffDeadEnd();
                LOG.fine("Dead end; vracam se na optimal: " + optimalAncestor.obj);
                return optimalAncestor.obj;
            }
        }
        LOG.fine("Sve OK; idem dalje na " + children.get(nextChild));
        return children.get(nextChild++);
    }

    public Paths rollback() {
        while(nodes.peek().isDeadEnd()) nodes.poll(); //eliminisem dead endove ako postoje
        return nodes.peek();
    }

    /**
     * Nedovrsena
     */
    private void cutoffDeadEnd() {
        Paths firstOccurence = this;
        while(firstOccurence.parent != null && firstOccurence.optimalAncestor == firstOccurence.parent.optimalAncestor) {
            firstOccurence = firstOccurence.parent;
        }
        Paths optimal;
        if(firstOccurence.parent != null)
            optimal = firstOccurence.parent.optimalAncestor.obj;

    }

    /**
     * Proverava da li je efikasnost zadovoljavajuca. Zapravo kljucna metoda.
     * v3: smanjuje minEfficiency i uklanja dead endove po potrebi
     * @return true ako jeste, false ako nije
     * @see #efficiency
     * @see #minEfficiency
     * @see #MAX_EFFICIENCY_FAILS
     * @see #REDUCE_EFFICIENCY_FACTOR
     */
    private boolean testEfficiency() {
        if(efficiency != EFFICIENCY_EMPTY && efficiency < minEfficiency) {
            efficiencyFails++;
            if(efficiencyFails==MAX_EFFICIENCY_FAILS) {
                efficiencyFails=0;
                if(!USE_NODE_QUEUE) {
                    if (optimalAncestor.obj.efficiency < minEfficiency) {
                        minEfficiency /= REDUCE_EFFICIENCY_FACTOR;
                    }
                    if (optimalAncestor.obj.isDeadEnd())
                        cutoffDeadEnd();
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Proverava da li ova stanica moze da ima jos dece
     */
    private boolean isDeadEnd() {
        return !currentStation.hasNext(currentLine);
    }

    /**
     * Proverava da li je pronasao cilj
     * @return true ako je ruta zavrsena, false ako nije
     * @see Base#getGoal()
     */
    public boolean isComplete() {
        return currentStation.equals(Base.getInstance().getGoal());
    }

    /**
     *
     * @return rekonstruisana putanja
     */
    public FullPath getPath() {
        List<FullPath.LineStationPair> path = new LinkedList<>();
        Paths node = this;
        do {
            path.add(new FullPath.LineStationPair(node.currentLine, node.currentStation));
            node = node.parent;
        } while(node != null);
        Collections.reverse(path);
        return new FullPath(path, cost);
    }

    @Override
    public String toString() {
        return currentLine.getId() + ": " + currentStation.getName(); //debugging purposes
    }

    @Override
    public int compareTo(@NotNull Paths o) {
        if (efficiency > o.efficiency)
            return -1;
        else if (efficiency < o.efficiency)
            return 1;
        else return 0;
    }

    /**
     * Referenca na neki Paths objekat
     */
    private static class Reference {
        Paths obj;
        Reference(Paths obj) {
            this.obj = obj;
        }
    }

    /**
     * Cuva listu linija i stanica i ukupan trosak
     */
    public static class FullPath {
        public static final int COST_TO_MIN_RATIO = 800; //pretty random

        private final List<LineStationPair> path;
        private final double cost;

        public FullPath(List<LineStationPair> path, double cost) {
            this.path = path;
            this.cost = cost;
        }

        public void print() {
            boolean terminal = UserIO.isLaunchedFromTerminal() || Config.DEBUG;
            StringBuilder buffer = new StringBuilder();
            if (terminal) System.out.println("Ruta: ");
            else buffer.append("Ruta\n");
            int brojStanica = 0;
            if(terminal) System.out.println(path.get(0)); //initial
            else buffer.append(path.get(0)).append("\n");
            LineStationPair prev = path.get(1);
            for(int i=2; i<path.size(); i++) {
                if(path.get(i).line.equals(prev.line)) {
                    brojStanica++;
                } else {
                    if(prev.line.isInitial() || prev.line.isWalking()) {
                        if (terminal) System.out.println(path.get(i - 1));
                        else buffer.append(path.get(i - 1)).append("\n");
                    } else {
                        if (terminal)
                            System.out.println(path.get(i - 1) + " (broj stanica: " + (brojStanica + 1) + ")"); //todo padezi
                        else buffer.append(path.get(i - 1)).append(" (broj stanica: ").append(brojStanica + 1).append(")").append("\n");
                    }
                    prev = path.get(i);
                    brojStanica=0;
                }
            }
            if(prev.line.isInitial() || prev.line.isWalking()) {
                if (terminal) System.out.println(path.get(path.size() - 1));
                else buffer.append(path.get(path.size() - 1)).append("\n");
            } else {
                if (terminal) System.out.println(path.get(path.size() - 1) + " (broj stanica: " + (brojStanica + 1) + ")"); //todo padezi
                else buffer.append(path.get(path.size() - 1)).append(" (broj stanica: ").append(brojStanica + 1).append(")").append("\n");
            }
            String time = "Procenjeno vreme: " + Math.round(cost / COST_TO_MIN_RATIO) + "min";
            if (terminal) System.out.println(time);
            else buffer.append(time);
            if(!terminal) JOptionPane.showMessageDialog(null, buffer.toString());
        }

        public static class LineStationPair { //fml
            private final Line line;
            private final Station station;

            public LineStationPair(Line line, Station station) {
                this.line = line;
                this.station = station;
            }

            @Override
            public String toString() {
                if(line.isInitial()) {
                    return "Početna stanica: " + station;
                }
                if(line.isWalking()) {
                    return "Šetajte do stanice " + station;
                }
                return "Vozite se linijom " + line + " do " + station;
            }
        }
    }
}
