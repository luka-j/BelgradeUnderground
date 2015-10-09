package rs.luka.belgradeunderground.data;

import com.sun.istack.internal.NotNull;
import rs.luka.belgradeunderground.model.Line;
import rs.luka.belgradeunderground.model.Station;

import java.util.*;
import java.util.stream.Collectors;

/**
 * v4
 * Jednostavniji, ne nuzno bolji. Nastao kao 'stabilnija' alternativa (nedovršenom) Paths-u. Dobijeni rezultati su slični
 * {@link rs.luka.belgradeunderground.Main.PathConfigs#ALWAYS_TEST_EFFICIENCY} parametrima za Paths, što nije uvek
 * optimalno. Generalno, manje se oslanja na procene iz {@link Station#getNextBestGuesses(Line)} nego druga klasa.
 * Dodaje node-ove u Queue u 'talasima' koje dobija iz {@link Station#getNextBestGuesses(Line)} i uvek vraća Node
 * koji se nalazi na vrhu, tj. onaj s najvećom efikasnošću i tako dok ne dođe do cilja. Jedini način da se neki Node
 * 'skloni' s vrha je da ga neki drugi 'prestigne' u efikasnosti ili da Node ne može da ima više dece (kad {@link
 * Station#getNextBestGuesses(Line)} počne da vraća prazne liste), kada se na vrh postavlja drugi po efikasnosti.
 * 'Queue' potiče iz naziva klase koju proširuje; inače je heap kao struktura podataka (kao i PriorityQueue. Ko uopšte
 * bira ovakva imena?)
 * Created by luka on 8.10.15.
 */
public class PathQueue extends PriorityQueue<PathQueue.Node> {

    private static final int INITIAL_SIZE = 513; //todo benchmark

    /**
     * Vraca Queue efikasnosti za datu stanicu
     * @param start pocetna stanica, koren
     */
    public PathQueue(Station start) {
        super(INITIAL_SIZE);
        Node initial = new Node(null, start, Line.getInitial(), 0.01); //0.01 jer izbegavam deljenje sa nulom
        this.addAll(initial.getNext()); //svejedno da li se dodaje initial ili initial#getNext(), posto initial ionako
        //pada na poslednje mesto sa efficiency==0
    }

    /**
     *
     * @return
     */
    public Node solve() {
        Node current;
        do {
            Set<Node> children;
            while ((children = peek().getNext()).size() == 0) poll();
            addAll(children);
            current = peek();
        } while (!current.isGoal());
        return current;
    }

    /**
     * Ekvivalent Paths-u ili PathTree-u
     */
    public static class Node implements Comparable<Node> {
        private Station currentStation;
        private Line currentLine;
        private double efficiency;
        private double cost;
        private Node parent;

        private Node(Node parent, Station station, Line line, double cost) {
            this.parent = parent;
            this.currentStation = station;
            this.currentLine = line;
            this.cost = cost;
            this.efficiency = Base.getInstance().getStart().getLocation().distanceBetween(currentStation.getLocation()) / cost;
        }

        private Set<Node> getNext() {
            return currentStation.getNextBestGuesses(currentLine).stream()
                    .map((Station.Link l) -> new Node(this, l.getToStation(), l.getUsingLine(), cost + l.calculateCostFor(currentLine)))
                    .collect(Collectors.toSet());
        }

        public boolean isGoal() {
            return currentStation.equals(Base.getInstance().getGoal());
        }

        public Paths.FullPath reconstruct() { //todo kopirati FullPath ovde, kako PathQueue ne bi zavisio od Paths
            List<Paths.FullPath.LineStationPair> path = new LinkedList<>();
            Node current = this;
            while (current != null) {
                path.add(new Paths.FullPath.LineStationPair(current.currentLine, current.currentStation));
                current = current.parent;
            }
            Collections.reverse(path);
            return new Paths.FullPath(path, cost);
        }

        @Override
        public int compareTo(@NotNull Node o) {
            if (efficiency > o.efficiency)
                return -1;
            else if (efficiency < o.efficiency)
                return 1;
            else return 0;
        }
    }
}
