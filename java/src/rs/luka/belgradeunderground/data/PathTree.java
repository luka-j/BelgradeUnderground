package rs.luka.belgradeunderground.data;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.model.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by luka on 17.9.15.
 */
public class PathTree {
    private static final int MAX_COST_DELTA = 600;
    public static final double STARTING_MIN_EFFICIENCY = 0.4;
    public static final double REDUCE_MIN_EFFICIENCY_FACTOR = 1.3;
    public static final int CALCULATE_EFFICIENCY_EVERY = 1; //if I were you, I'd keep this on 1
    public static final int MAX_EFFICIENCY_FAILS = 3;
    private static final int EFFICIENCY_EMPTY = -1;

    public static final int PATH_NOT_FOUND_ROLLBACK = -1;
    public static final int PATH_NOT_FOUND_ROOT = 0;
    public static final int PATH_NOT_FOUND_PARENT = 1; //specijalni slucaj ancestora
    public static final int PATH_NOT_FOUND_ANCESTOR = 3; //menjati po zelji
    public static final int HANDLE_PATH_NOT_FOUND_METHOD = PATH_NOT_FOUND_ROLLBACK;

    private List<PathTree> snapshots = new LinkedList<>(); //flattened tree, jer memorije imam

    private double minEfficiency = 0;
    private int efficiencyFails = 0;

    private final PathTree parent;
    private final List<PathTree> children = new LinkedList<>(); //trenutno nekoristeno, posto koristim listu (snapshots) za traversal

    private final Station currentStation;
    private final Line currentLine;
    private final int level;
    private final double cost;
    private final double efficiency;

    private PathTree(PathTree parent, Station currentStation, Line currentLine, double cost, double minEfficiency,
                     int efficiencyFails, List<PathTree> previousSnapshots) {
        level = (parent == null ? 0 : (parent.level + 1)); //redundant parens
        this.parent = parent;
        this.currentStation = currentStation;
        this.currentLine = currentLine;
        this.cost = cost;
        this.minEfficiency = minEfficiency;
        this.efficiencyFails = efficiencyFails;
        this.snapshots = previousSnapshots;
        if(level > 0 && level % CALCULATE_EFFICIENCY_EVERY == 0) {
            efficiency = Base.getInstance().getStart().getLocation().distanceBetween(currentStation.getLocation())/cost;
            snapshots.add(this);
        } else {
            efficiency = EFFICIENCY_EMPTY;
        }
    }

    public static PathTree init(Station station) {
        return new PathTree(null, station, Line.getInitial(), 0, STARTING_MIN_EFFICIENCY, 0, new LinkedList<>());
    }

    public PathTree add(Station.Link usingLink, Line currentLine) {
        if(usingLink == null) {
            switch (HANDLE_PATH_NOT_FOUND_METHOD) {
                case PATH_NOT_FOUND_ROLLBACK: return rollbackToChild();
                case PATH_NOT_FOUND_ROOT:
                    PathTree root = parent;
                    while(root.level > 0) root = root.parent;
                    return root;
                case PATH_NOT_FOUND_PARENT: return ancestor(1);
                case PATH_NOT_FOUND_ANCESTOR: return ancestor(PATH_NOT_FOUND_ANCESTOR);
                default: throw new IllegalArgumentException("Invalid path not found method: " + HANDLE_PATH_NOT_FOUND_METHOD);
            }
        }
        if(efficiency != EFFICIENCY_EMPTY) {
            if (efficiency < minEfficiency) {
                if (efficiencyFails == MAX_EFFICIENCY_FAILS)
                    return rollbackToChild();
                else
                    efficiencyFails++;
            } else {
                efficiencyFails = 0;
            }
        }
        double nextCost = usingLink.calculateCostFor(currentLine);
        PathTree next = new PathTree(this, usingLink.getToStation(), usingLink.getUsingLine(),
                cost + nextCost, minEfficiency, efficiencyFails, snapshots);
        children.add(next);
        return next;
    }

    //todo fix
    public PathTree putNext() {
        double nextWeight = currentStation.peekNextWeight(currentLine);
        PathTree best = add(currentStation.getNextBestGuess(currentLine), currentLine);

        while(currentStation.hasNext(currentLine) && nextWeight + MAX_COST_DELTA > currentStation.peekNextWeight(currentLine)) {
            Station.Link next = currentStation.getNextBestGuess(currentLine);
            children.add(new PathTree(this, next.getToStation(), next.getUsingLine(),
                    cost + next.calculateCostFor(currentLine), minEfficiency, efficiencyFails, snapshots));
        }

        return best;
    }

    private PathTree ancestor(int level) {
        PathTree currLevel = this;
        for(int i=0; i<level; i++)
            currLevel = currLevel.parent!=null ? currLevel.parent : currLevel;
        return currLevel;
    }

    public boolean isComplete() {
        return currentStation.equals(Base.getInstance().getGoal()); //trebalo bi da moze i ==, s obzirom da svi objekti poticu iz baze
    }

    public Station getCurrentStation() {
        return currentStation;
    }

    public Line getCurrentLine() {
        return currentLine;
    }

    public Result getPath() {
        List<LineStationPair> path = new LinkedList<>();
        PathTree node = this;
        do {
            path.add(new LineStationPair(node.currentLine, node.currentStation));
            node = node.parent;
        } while(node != null);
        Collections.reverse(path);
        return new Result(path, cost);
    }

    private PathTree rollback() {
        PathTree maxEfficiencyNode = null, node;
        double max = -1;
        for (PathTree snapshot : snapshots) {
            node = snapshot;
            if (node.efficiency != EFFICIENCY_EMPTY && node.efficiency > max && node.currentStation.hasNext(node.currentLine)) {
                max = node.efficiency;
                maxEfficiencyNode = node;
            }
        }
        if(maxEfficiencyNode == null) {
            if(Config.DEBUG) System.err.println("Ne mogu da nadjem node za rollback!");
            return this;
        }
        if(maxEfficiencyNode.efficiency < minEfficiency) { //whoops
            minEfficiency /= REDUCE_MIN_EFFICIENCY_FACTOR;
            if(maxEfficiencyNode.efficiency < minEfficiency) {
                minEfficiency /= REDUCE_MIN_EFFICIENCY_FACTOR;
                if(Config.DEBUG) System.err.println("Ne radi ti algoritam. (PathTree.java:140)");
            }
        }
        maxEfficiencyNode.snapshots = snapshots;
        maxEfficiencyNode.minEfficiency = minEfficiency; //bez ovoga se vrti u krug
        return maxEfficiencyNode;
    }

    //todo fix
    public PathTree rollbackToChild() { //koristi Listu children, za razliku od #rollback koji se oslanja na snapshots
        double max = -1;
        PathTree current = this, maxEfficiencyNode = null;
        do {
            for(PathTree node : current.children) {
                if(node.efficiency != EFFICIENCY_EMPTY && node.efficiency > max && node.currentStation.hasNext(node.currentLine)) {
                    max = node.efficiency;
                    maxEfficiencyNode = node;
                }
            }
            current = current.parent;
        } while(current != null);
        if(maxEfficiencyNode == null) {
            if(Config.DEBUG) System.err.println("Ne mogu da nadjem node za rollback!");
            //return this;
            return null;
        }
        if(maxEfficiencyNode.efficiency < minEfficiency) { //whoops
            minEfficiency /= REDUCE_MIN_EFFICIENCY_FACTOR;
            if(maxEfficiencyNode.efficiency < minEfficiency) {
                minEfficiency /= REDUCE_MIN_EFFICIENCY_FACTOR;
                if(Config.DEBUG) System.err.println("Ne radi ti algoritam. (PathTree.java:184)");
            }
        }
        //maxEfficiencyNode.snapshots = snapshots;
        maxEfficiencyNode.minEfficiency = minEfficiency;
        return maxEfficiencyNode;
    }

    public double getCost() {
        return cost;
    }

    private static class LineStationPair {
        private final Line line;
        private final Station station;

        public LineStationPair(Line line, Station station) {
            this.line = line;
            this.station = station;
        }

        @Override
        public String toString() {
            return line.getId() + ": " + station.getName();
        }
    }

    public static class Result { //too many nested classes.
        private final List<LineStationPair> path;
        private final double cost;

        public Result(List<LineStationPair> path, double cost) {
            this.path = path;
            this.cost = cost;
        }

        public void print() { //todo proper
            System.out.println("Route");
            for(LineStationPair stop : path) {
                System.out.println(stop.toString());
            }
            System.out.println("Cost: " + cost);
        }

        public double getCost() {
            return cost;
        }
    }
}
